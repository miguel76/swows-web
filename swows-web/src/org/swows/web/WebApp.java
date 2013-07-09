package org.swows.web;

import java.awt.Color;
import java.awt.GraphicsConfiguration;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.batik.dom.events.DOMMutationEvent;
import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.gvt.GVTTreeRendererAdapter;
import org.apache.batik.swing.gvt.GVTTreeRendererEvent;
import org.apache.batik.swing.svg.GVTTreeBuilderAdapter;
import org.apache.batik.swing.svg.GVTTreeBuilderEvent;
import org.apache.batik.swing.svg.SVGDocumentLoaderAdapter;
import org.apache.batik.swing.svg.SVGDocumentLoaderEvent;
import org.apache.batik.util.RunnableQueue;
import org.swows.graph.DynamicDatasetMap;
import org.swows.graph.EventCachingGraph;
import org.swows.graph.events.DynamicGraph;
import org.swows.graph.events.DynamicGraphFromGraph;
import org.swows.mouse.MouseApp;
import org.swows.mouse.MouseInput;
import org.swows.producer.DataflowProducer;
import org.swows.runnable.RunnableContext;
import org.swows.runnable.RunnableContextFactory;
import org.swows.time.SystemTime;
import org.swows.vocabulary.SWI;
import org.swows.xmlinrdf.DocumentReceiver;
import org.swows.xmlinrdf.DomDecoder2;
import org.swows.xmlinrdf.DomEventListener;
import org.swows.xmlinrdf.EventManager;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Node;

public class WebApp implements EventManager {

	private static final long serialVersionUID = 1L;
//	private RunnableQueue batikRunnableQueue = null;
	private EventCachingGraph cachingGraph = null;
	private boolean docLoadedOnClient = false;
	private Document document = null;
//	private boolean docToBeRealoaded = false;
	private DOMImplementation domImpl;
	private final WebInput webInput = new WebInput();
	
	private static final String JS_CALLBACK_FUNCTION_NAME = "swowsEvent";
	private static final String JS_CALLBACK = JS_CALLBACK_FUNCTION_NAME + "()";
//	private static final String JS_CALLBACK_BODY = "var req = new XMLHttpRequest(); req.open('POST','',false); req.send(evt);";
	private static final String JS_CALLBACK_BODY =
			"var reqTxt = '" +
					"@prefix evt: <http://www.swows.org/DOM/Events#>. " +
					"_:newEvent a evt:Event; '; " +
//			"for (var i = 0; i < evt.length; i++) { " +
//				"reqText += '<' + evt[i] + '>'; " +
//			"} " +
			"reqTxt += '" +
					"evt:target <' + tn(evt.target).getAttribute('resource') + '>; " +
					"evt:currentTarget <' + tn(evt.currentTarget).getAttribute('resource') + '>; " +
					"evt:type \"' + evt.type + '\".'; " +
			"var req = new XMLHttpRequest(); req.open('POST','',false); " +
			"req.send(reqTxt); " +
			"alert(req.responseText); "; // TODO:will be eval instead of alert
	private static final String JS_TARGET_CB_FUNCTION = "var tn = function (t) { return t.correspondingUseElement ? t.correspondingUseElement : t }; ";
	private static final String CHARACTER_ENCODING = "UTF-8";
	private static final String JS_CONTENT_TYPE = "application/javascript";
	
	private StringBuffer clientCommandsCache = null;
	private static final int CLIENT_COMMANDS_CACHE_CAPACITY = 256;
	private static final String CLIENT_COMMANDS_SEP = ";";
	
	// TODO: it would be possible useful to use deflate/inflate for client server communication
	// Server-side: http://docs.oracle.com/javase/1.5.0/docs/api/java/util/zip/Deflater.html
	// Client-side: https://github.com/dankogai/js-deflate
	// Maybe for http is possible to use browser native decompression
	
	private void addClientCommand(String command) {
		if (command != null) {
			if (clientCommandsCache == null)
				clientCommandsCache =
						new StringBuffer(CLIENT_COMMANDS_CACHE_CAPACITY);
			clientCommandsCache.append(command);
			clientCommandsCache.append(CLIENT_COMMANDS_SEP);
		}
	}
	
	private void addDOMListeners() {
		
		//TODO: quite everything to be done here
		
        EventListener domEventListener =
				new EventListener() {
					@Override
					public void handleEvent(Event event) {
						DOMMutationEvent domEvent = (DOMMutationEvent) event;
						System.out.println("*** DOM Changed Event START ***");
						System.out.println("Event type: " + domEvent.getType());
						System.out.println("Target: " + domEvent.getTarget());
						System.out.println("Attr Name: " + domEvent.getAttrName());
						System.out.println("Attr Change Type: " + domEvent.getAttrChange());
						System.out.println("Attr New Value: " + domEvent.getNewValue());
						System.out.println("Attr Prev Value: " + domEvent.getPrevValue());
						System.out.println("Related Node: " + domEvent.getRelatedNode());
						System.out.println("*** DOM Changed Event END ***");
					}
				};
				
		EventListener domGenericEventListener =
				new EventListener() {
					@Override
					public void handleEvent(Event event) {
				      	DOMImplementationLS feature = (DOMImplementationLS) domImpl.getFeature("LS",
				        		"3.0");
				        LSSerializer serializer = feature.createLSSerializer();
				        LSOutput output = feature.createLSOutput();
				        OutputStream os;
						try {
							os = new FileOutputStream("/home/miguel/tmp/Result.svg");
						} catch (FileNotFoundException e) {
							e.printStackTrace();
							throw new RuntimeException(e);
						}
				        output.setByteStream(os);
				        serializer.write(document, output);
					}
				};
						
//        ((EventTarget) xmlDoc)
//        		.addEventListener(
//        				"DOMSubtreeModified",
//        				domGenericEventListener,
//						false);
//
//        ((EventTarget) xmlDoc)
//				.addEventListener(
//						"DOMNodeInserted",
//						domEventListener,
//						false);
//        ((EventTarget) xmlDoc)
//				.addEventListener(
//						"DOMNodeRemoved",
//						domEventListener,
//						false);
//        ((EventTarget) xmlDoc)
//				.addEventListener(
//						"DOMNodeRemovedFromDocument",
//						domEventListener,
//						false);
//        ((EventTarget) xmlDoc)
//				.addEventListener(
//						"DOMNodeInsertedIntoDocument",
//						domEventListener,
//						false);
//        ((EventTarget) xmlDoc)
//				.addEventListener(
//						"DOMNodeInserted",
//						domEventListener,
//						false);
				
        ((EventTarget) document)
				.addEventListener(
						"DOMAttrModified",
						new EventListener() {
							@Override
							public void handleEvent(Event event) {
								DOMMutationEvent domEvent = (DOMMutationEvent) event;
//								() domEvent.getTarget();
								System.out.println("Attr Name: " + domEvent.getAttrName());
								System.out.println("Attr Change Type: " + domEvent.getAttrChange());
								System.out.println("Attr New Value: " + domEvent.getNewValue());
								System.out.println("Attr Prev Value: " + domEvent.getPrevValue());
								
							}
						},
						false);
//        ((EventTarget) xmlDoc)
//				.addEventListener(
//						"DOMCharacterDataModified",
//						domEventListener,
//						false);

		
	}
	
	private void setDocument(Document newDocument) {
		document = newDocument;
		document.getDocumentElement().setAttribute(
				"onload",
				JS_TARGET_CB_FUNCTION + "var " + JS_CALLBACK_FUNCTION_NAME + " = function (evt) { " + JS_CALLBACK_BODY +" }; " + genAddEventListeners() + " alert('loaded');");
		addDOMListeners();
	}

	public WebApp(
			Graph dataflowGraph//,
			) {
		RunnableContextFactory.setDefaultRunnableContext(new RunnableContext() {
			@Override
			public synchronized void run(final Runnable runnable) {
//				try {
					while (!docLoadedOnClient || cachingGraph == null) Thread.yield();
//					final long start = System.currentTimeMillis();
					runnable.run();
//							long afterCascade = System.currentTimeMillis();
//							System.out.println(
//									"RDF envent cascade executed in "
//											+ (afterCascade - runEntered) + "ms" );
					cachingGraph.sendEvents();
//							long afterSvgDom = System.currentTimeMillis();
//							System.out.println(
//									"SVG DOM updated in "
//											+ (afterSvgDom - afterCascade) + "ms" );
//					long runFinished = System.currentTimeMillis();
//					System.out.println(
//							"SVG updated and repainted in "
//									+ (runFinished - start + "ms" ) );
					if (!docLoadedOnClient) {
						askForReload();
					}
//				} catch(InterruptedException e) {
//					throw new RuntimeException(e);
//				}
			}

		});
//    	final WebInput webInput = new WebInput();
//    	final SystemTime systemTime = new SystemTime();
    	final DynamicDatasetMap inputDatasetGraph = new DynamicDatasetMap(webInput.getGraph());
    	inputDatasetGraph.addGraph(Node.createURI(SWI.getURI() + "mouseEvents"), webInput.getGraph());
		DataflowProducer applyOps =	new DataflowProducer(new DynamicGraphFromGraph(dataflowGraph), inputDatasetGraph);
		DynamicGraph outputGraph = applyOps.createGraph(inputDatasetGraph);
		cachingGraph = new EventCachingGraph(outputGraph);
//		cachingGraph = new EventCachingGraph( new LoggingGraph(outputGraph, Logger.getRootLogger(), true, true) );
        
		try {
			domImpl = DOMImplementationRegistry.newInstance().getDOMImplementation("XML 3.0");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (ClassCastException e) {
			throw new RuntimeException(e);
		}
                
//		Set<DomEventListener> domEventListenerSet = new HashSet <DomEventListener>();
//		domEventListenerSet.add(mouseInput);
//		Map<String,Set<DomEventListener>> domEventListeners = new HashMap <String,Set<DomEventListener>>();
//		domEventListeners.put("click", domEventListenerSet);
//		domEventListeners.put("mousedown", domEventListenerSet);
//		domEventListeners.put("mouseup", domEventListenerSet);
                
		setDocument(
				DomDecoder2.decodeOne(
						cachingGraph,
//						outputGraph,
//						new LoggingGraph(cachingGraph, Logger.getRootLogger(), true, true),
						domImpl /*,
						new RunnableContext() {
							@Override
							public void run(Runnable runnable) {
								try {
									batikRunnableQueue.invokeAndWait(runnable);
								} catch(InterruptedException e) {
									throw new RuntimeException(e);
								}
							}
						} */,
						new DocumentReceiver() {
//							{
//								(new Thread() {
//									public void run() {
//										while (true) {
//											while (newDocument == null) yield();
//											RunnableQueue runnableQueue = batikRunnableQueue;
//											runnableQueue.suspendExecution(true);
//											batikRunnableQueue = null;
////											batikRunnableQueue.getThread().halt();
////											batikRunnableQueue = null;
//											svgCanvas.setDocument(newDocument);
//											newDocument = null;
//											batikRunnableQueue.resumeExecution();
//										}
//									}
//								}).start();
//							}
//							private Document newDocument = null;
							@Override
							public void sendDocument(Document doc) {
								setDocument(doc);
								docLoadedOnClient = false;
							}
                                                                
						},
//						domEventListeners,
						this));
		

//        TransformerFactory transformerFactory = TransformerFactory.newInstance();
//		Transformer transformer;
//		try {
//			transformer = transformerFactory.newTransformer();
//			DOMSource source = new DOMSource(xmlDoc);
//			StreamResult result =  new StreamResult(System.out);
//			transformer.transform(source, result);
//		} catch (TransformerException e) {
//			e.printStackTrace();
//		}

	}

	private void askForReload() {
		// TODO Auto-generated method stub
		
	}
	
	private String clientElementIdentifier(Element element) {
		NamedNodeMap attrs = element.getAttributes();
		for (int attrIndex = 0; attrIndex < attrs.getLength(); attrIndex++ ) {
			Attr attr = (Attr) attrs.item(attrIndex);
			if (attr.isId() 
					|| attr.getName().equalsIgnoreCase("id") // TODO: delete this two lines of workaround and find better way to manage id attrs
					|| attr.getName().equals("xml:id") )
				return "document.getElementById('" + attr.getValue() + "')";
		}
		return "boh"; // TODO: extend it to uniquely identify each node via its path from nearest ancestor with id
	}
	
	Map<org.w3c.dom.Node, Set<String>> listenedNodeAndTypes = new HashMap<org.w3c.dom.Node, Set<String>>();

	private String genAddEventListener(
			org.w3c.dom.Node target,
			String type,
			boolean useCapture) {
		if (target instanceof Element) {
			return clientElementIdentifier((Element) target)
								+ ".addEventListener( '"
								+ type + "', "
								+ JS_CALLBACK_FUNCTION_NAME + ", "
								+ useCapture + " )";
		}
		return "";
	}
	
	private String genAddEventListeners() {
		StringBuffer buffer = new StringBuffer(CLIENT_COMMANDS_CACHE_CAPACITY);
		for (org.w3c.dom.Node target : listenedNodeAndTypes.keySet())
			for (String type : listenedNodeAndTypes.get(target))
				buffer
						.append( genAddEventListener(target, type, false) )
						.append( CLIENT_COMMANDS_SEP ); // TODO: manage useCapture if to be used at all
		return buffer.toString();
	}
	
	@Override
	public void addEventListener(
			Node targetNode, org.w3c.dom.Node target, String type,
			EventListener listener, boolean useCapture) {
//			((Element) target).setAttribute("on" + type, JS_CALLBACK);
		Set<String> listenedTypesForTarget = listenedNodeAndTypes.get(target);
		if (listenedTypesForTarget == null) {
			listenedTypesForTarget = new HashSet<String>();
			listenedNodeAndTypes.put(target, listenedTypesForTarget);
		}
		listenedTypesForTarget.add(type);
		if (docLoadedOnClient)
			addClientCommand( genAddEventListener(target, type, useCapture) );
	}

	@Override
	public void removeEventListener(
			Node targetNode, org.w3c.dom.Node target, String type,
			EventListener listener, boolean useCapture) {
		Set<String> listenedTypesForTarget = listenedNodeAndTypes.get(target);
		if (listenedTypesForTarget != null) {
			listenedTypesForTarget.remove(type);
			if (listenedTypesForTarget.isEmpty())
				listenedNodeAndTypes.remove(target);
		}
		if (target instanceof Element) {
//			((Element) target).removeAttribute("on" + type);
			if (docLoadedOnClient)
				addClientCommand(
						clientElementIdentifier((Element) target)
								+ ".removeEventListener( '"
								+ type + "', "
								+ JS_CALLBACK_FUNCTION_NAME + ", "
								+ useCapture + " )");
		}
	}
	
	private void sendEntireDocument(HttpServletResponse response) throws IOException {
		response.setContentType("image/svg+xml");
//		response.setContentType("text/html");
	    OutputStream out = response.getOutputStream();
      	DOMImplementationLS feature = (DOMImplementationLS) domImpl.getFeature("LS",
        		"3.0");
        LSSerializer serializer = feature.createLSSerializer();
        LSOutput output = feature.createLSOutput();
        output.setByteStream(out);
        serializer.write(document, output);
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		sendEntireDocument(response);
	}
	
	public synchronized void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType(JS_CONTENT_TYPE);
		response.setCharacterEncoding(CHARACTER_ENCODING);
//		response.setContentType("text/html");
	    OutputStream out = response.getOutputStream();
	    Writer writer = new OutputStreamWriter(out,CHARACTER_ENCODING);
	    
	    BufferedReader eventReader = request.getReader();
	    StringBuffer eventSB = new StringBuffer();
	    while (true) {
	    	String eventLine = eventReader.readLine();
	    	if (eventLine == null)
	    		break;
	    	eventSB.append(eventLine);
	    }
	    webInput.handleEvent(eventSB.toString());
	    
	    if (clientCommandsCache != null) {
	    	writer.write(clientCommandsCache.toString());
	    	clientCommandsCache = null;
		    writer.flush();
	    }	    
	    // copying input to output just to test it
	    //writer.write(eventSB.toString());
//	    writer.flush();
	}

//	public Document getDocument() {
//		return document;
//	}
	
}
