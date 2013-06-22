package org.swows.web;

import java.awt.Color;
import java.awt.GraphicsConfiguration;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
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
	
	private static String JS_CALLBACK = "swowsEvent()";
	
	private StringBuffer clientCommandsCache = null;
	private static final int CLIENT_COMMANDS_CACHE_CAPACITY = 256;
	private static final String CLIENT_COMMANDS_SEP = ";";
	
	private void addClientCommand(String command) {
		if (command != null) {
			if (clientCommandsCache == null)
				clientCommandsCache =
						new StringBuffer(CLIENT_COMMANDS_CACHE_CAPACITY);
			clientCommandsCache.append(command);
			clientCommandsCache.append(CLIENT_COMMANDS_SEP);
		}
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
    	final MouseInput mouseInput = new MouseInput();
    	final SystemTime systemTime = new SystemTime();
    	final DynamicDatasetMap inputDatasetGraph = new DynamicDatasetMap(systemTime.getGraph());
    	inputDatasetGraph.addGraph(Node.createURI(SWI.getURI() + "mouseEvents"), mouseInput.getGraph());
		DataflowProducer applyOps =	new DataflowProducer(new DynamicGraphFromGraph(dataflowGraph), inputDatasetGraph);
		DynamicGraph outputGraph = applyOps.createGraph(inputDatasetGraph);
		cachingGraph = new EventCachingGraph(outputGraph);
//		cachingGraph = new EventCachingGraph( new LoggingGraph(outputGraph, Logger.getRootLogger(), true, true) );
        
		DOMImplementation domImpl = SVGDOMImplementation.getDOMImplementation();
                
		Set<DomEventListener> domEventListenerSet = new HashSet <DomEventListener>();
		domEventListenerSet.add(mouseInput);
		Map<String,Set<DomEventListener>> domEventListeners = new HashMap <String,Set<DomEventListener>>();
		domEventListeners.put("click", domEventListenerSet);
		domEventListeners.put("mousedown", domEventListenerSet);
		domEventListeners.put("mouseup", domEventListenerSet);
                
		document =
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
								document = doc;
								docLoadedOnClient = false;
							}
                                                                
						},
						domEventListeners, this);

     /*   EventTarget t = (EventTarget) xmlDoc;

        if (EventsProducer.getEventsProducer() == null) {
            try {
                             
              EventsProducer.setEventsProducer();      
            } catch (java.lang.ExceptionInInitializerError ex) {
                ex.printStackTrace();
                ex.getCause();
            }
        }

       t.addEventListener("click", new EventListener() {

            public void handleEvent(Event evt) {
                EventsProducer.getEventsProducer().update(evt);
                
            }
        }, false);

*/
        
//        DOMImplementation implementation = null;
//		try {
//			implementation = DOMImplementationRegistry.newInstance()
//					.getDOMImplementation("XML 3.0");
//		} catch (ClassCastException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		} catch (ClassNotFoundException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		} catch (InstantiationException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		} catch (IllegalAccessException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//      	DOMImplementationLS feature = (DOMImplementationLS) implementation.getFeature("LS",
//        		"3.0");
//        LSSerializer serializer = feature.createLSSerializer();
//        LSOutput output = feature.createLSOutput();
////        output.setByteStream(System.out);
//        
//        OutputStream os;
//		try {
//			os = new FileOutputStream("/home/miguel/tmp/Result.svg");
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//			throw new RuntimeException(e);
//		}
//        output.setByteStream(os);
//        serializer.write(xmlDoc, output);
        
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
				        DOMImplementation implementation = null;
						try {
							implementation = DOMImplementationRegistry.newInstance()
									.getDOMImplementation("XML 3.0");
						} catch (ClassCastException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						} catch (ClassNotFoundException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						} catch (InstantiationException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						} catch (IllegalAccessException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
				      	DOMImplementationLS feature = (DOMImplementationLS) implementation.getFeature("LS",
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
////        ((EventTarget) xmlDoc)
////				.addEventListener(
////						"DOMAttrModified",
////						domEventListener,
////						false);
//        ((EventTarget) xmlDoc)
//				.addEventListener(
//						"DOMCharacterDataModified",
//						domEventListener,
//						false);


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

	@Override
	public void addEventListener(org.w3c.dom.Node target, String type,
			EventListener listener, boolean useCapture) {
		if (target instanceof Element)
			((Element) target).setAttribute("on" + type, JS_CALLBACK);
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeEventListener(org.w3c.dom.Node target, String type,
			EventListener listener, boolean useCapture) {
		// TODO Auto-generated method stub
		
	}

	
}
