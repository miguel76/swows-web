package org.swows.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.swows.graph.DynamicDatasetMap;
import org.swows.graph.EventCachingGraph;
import org.swows.graph.events.DynamicGraph;
import org.swows.graph.events.DynamicGraphFromGraph;
import org.swows.producer.DataflowProducer;
import org.swows.runnable.RunnableContext;
import org.swows.runnable.RunnableContextFactory;
import org.swows.vocabulary.DOMEvents;
import org.swows.vocabulary.SWI;
import org.swows.xmlinrdf.DocumentReceiver;
import org.swows.xmlinrdf.DomDecoder;
import org.swows.xmlinrdf.EventManager;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.events.MutationEvent;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.NodeFactory;

public class WebApp implements EventManager {

	private EventCachingGraph cachingGraph = null;
	private boolean docLoadedOnClient = false;
	private Document document = null;
//	private boolean docToBeRealoaded = false;
	private DOMImplementation domImpl;
	private final WebInput webInput = new WebInput();
	private static final Logger logger = Logger.getLogger(WebApp.class);
	
	private static final String JS_CALLBACK_FUNCTION_NAME = "swowsEvent";
//	private static final String JS_CALLBACK_BODY =
//			"var reqTxt = '" +
//					"@prefix evt: <http://www.swows.org/2013/07/xml-dom-events#>. " +
//					"_:newEvent a evt:Event; '; " +
////			"for (var i = 0; i < evt.length; i++) { " +
////				"reqText += '<' + evt[i] + '>'; " +
////			"} " +
//			"reqTxt += '" +
//					"evt:target <' + tn(evt.target).getAttribute('resource') + '>; " +
//					"evt:currentTarget <' + tn(evt.currentTarget).getAttribute('resource') + '>; " +
//					"evt:type \"' + evt.type + '\".'; " +
//			"var req = new XMLHttpRequest(); req.open('POST','',false); " +
//			"req.send(reqTxt); " +
////			"alert(req.responseText); " +
//			"eval(req.responseText); "; 
	private String jsCallbackBody; 
	private static final String JS_TARGET_CB_FUNCTION = "var tn = function (t) { return t.correspondingUseElement ? t.correspondingUseElement : t }; ";
	private static final String CHARACTER_ENCODING = "utf-8";
	private static final String JS_CONTENT_TYPE = "application/javascript";
	
	private StringBuffer clientCommandsCache = null;
	private static final int CLIENT_COMMANDS_CACHE_CAPACITY = 256;
	private static final String CLIENT_COMMANDS_SEP = ";";
	
	private int newNodeCount = 0;
	private Map<org.w3c.dom.Node, Integer> newNodeIds;
	
	private void resetCommandSet() {
		clientCommandsCache = null;
		newNodeCount = 0;
		newNodeIds = null;
	}
	
	private String newNode2varName(org.w3c.dom.Node node) {
		if (newNodeIds == null)
			newNodeIds = new HashMap<org.w3c.dom.Node, Integer>();
		Integer nodeId = newNodeIds.get(node);
		if (nodeId == null) {
			nodeId = newNodeCount++;
			newNodeIds.put(node, nodeId);
		}
		return "newNode_" + nodeId;
	}
	
	private String node2varName(org.w3c.dom.Node node) {
		if (newNodeIds == null)
			return null;
		Integer nodeId = newNodeIds.get(node);
		if (nodeId == null)
			return null;
		return "newNode_" + nodeId;
	}
	
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
		
		logger.debug("Started registering DOM mutation listeners");

//        ((EventTarget) xmlDoc)
//        		.addEventListener(
//        				"DOMSubtreeModified",
//        				domGenericEventListener,
//						false);
//
        ((EventTarget) document)
				.addEventListener(
						"DOMNodeInserted",
						new EventListener() {
							public void handleEvent(Event event) {
								logger.debug("DOMNodeInserted event");
								addNodeInsert((MutationEvent) event);
							}
						},
						false);
        ((EventTarget) document)
				.addEventListener(
						"DOMNodeRemoved",
						new EventListener() {
							public void handleEvent(Event event) {
								logger.debug("DOMNodeRemoved event");
								addNodeRemoval((MutationEvent) event);
							}
						},
						false);
        ((EventTarget) document)
				.addEventListener(
						"DOMNodeRemovedFromDocument",
						new EventListener() {
							public void handleEvent(Event event) {
								logger.debug("DOMNodeRemovedFromDocument event");
								addNodeRemovalFromDoc((MutationEvent) event);
							}
						},
						false);
        ((EventTarget) document)
				.addEventListener(
						"DOMNodeInsertedIntoDocument",
						new EventListener() {
							public void handleEvent(Event event) {
								logger.debug("DOMNodeInsertedIntoDocument event");
								addNodeCreation((MutationEvent) event);
							}
						},
						false);
				
        ((EventTarget) document)
				.addEventListener(
						"DOMAttrModified",
						new EventListener() {
							public void handleEvent(Event event) {
								logger.trace("DOMAttrModified event of type " + ((MutationEvent) event).getAttrChange());
								addAttrModify((MutationEvent) event);
							}
						},
						false);
        ((EventTarget) document)
				.addEventListener(
						"DOMCharacterDataModified",
						new EventListener() {
							public void handleEvent(Event event) {
								logger.trace("DOMCharacterDataModified event");
								addCharacterDataModify((MutationEvent) event);
							}
						},
						false);

		logger.debug("Ended registering DOM mutation listeners");
		
	}
	
	private void setOnload(String onloadBody) {
		Element docElem = document.getDocumentElement();
		if ( docElem.getNodeName().equals("html") || docElem.getLocalName().equals("html") )
			((Element) docElem.getLastChild()).setAttribute("onload", onloadBody);
//		((Element) docElem.getElementsByTagName("body").item(0)).setAttribute("onload", onloadBody);
		if ( docElem.getNodeName().equals("svg") || docElem.getLocalName().equals("svg") )
			docElem.setAttribute("onload", onloadBody);
	}
	
	private void setDocument(Document newDocument) {
		document = newDocument;
		setOnload( JS_TARGET_CB_FUNCTION + "var " + JS_CALLBACK_FUNCTION_NAME + " = function (evt) { " + jsCallbackBody +" }; " + genAddEventListeners() );
		addDOMListeners();
	}

	public WebApp(
			Graph dataflowGraph,
			String requestURL
			) {
		
		jsCallbackBody =
				"var reqTxt = '" +
						"@prefix evt: <" + DOMEvents.getURI() + "> . " +
						"_:newEvent a evt:Event; '; " +
				"reqTxt += '" +
						"evt:target <' + tn(evt.target).getAttribute('resource') + '> ; " +
						"evt:currentTarget <' + tn(evt.currentTarget).getAttribute('resource') + '> ; " +
						"evt:type \"' + evt.type + '\".'; " +
				"var req = new XMLHttpRequest(); req.open('POST','" + requestURL + "',false); " +
				"req.send(reqTxt); " +
				"eval(req.responseText); ";
		
		RunnableContextFactory.setDefaultRunnableContext(new RunnableContext() {
			public synchronized void run(final Runnable runnable) {
//				try {
//					while (!docLoadedOnClient || cachingGraph == null) Thread.yield();
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
    	inputDatasetGraph.addGraph(NodeFactory.createURI(SWI.getURI() + "mouseEvents"), webInput.getGraph());
		DataflowProducer applyOps =	new DataflowProducer(new DynamicGraphFromGraph(dataflowGraph), inputDatasetGraph);
		DynamicGraph outputGraph = applyOps.createGraph(inputDatasetGraph);
		cachingGraph = new EventCachingGraph(outputGraph);
//		cachingGraph = new EventCachingGraph( new LoggingGraph(outputGraph, Logger.getRootLogger(), true, true) );
        
		try {
			domImpl =
					(DOMImplementationRegistry.newInstance().getDOMImplementation("XML 3.0 +MutationEvents 2.0") != null)
					? DOMImplementationRegistry.newInstance().getDOMImplementation("XML 3.0 +MutationEvents 2.0") : domImpl;
			logger.debug("Loaded DOM implementation: " + domImpl);
//			logger.debug("XML DOM Level 1: " + domImpl.hasFeature("XML", "1.0"));
//			logger.debug("XML DOM Level 2: " + domImpl.hasFeature("XML", "2.0"));
//			logger.debug("XML DOM Level 3: " + domImpl.hasFeature("XML", "3.0"));
//			logger.debug("Core DOM Level 1: " + domImpl.hasFeature("Core", "1.0"));
//			logger.debug("Core DOM Level 2: " + domImpl.hasFeature("Core", "2.0"));
//			logger.debug("Core DOM Level 3: " + domImpl.hasFeature("Core", "3.0"));
//			logger.debug("Events DOM Level 1: " + domImpl.hasFeature("+Events", "1.0"));
//			logger.debug("Events DOM Level 2: " + domImpl.hasFeature("+Events", "2.0"));
//			logger.debug("Events DOM Level 3: " + domImpl.hasFeature("+Events", "3.0"));
//			logger.debug("MutationEvents DOM Level 1: " + domImpl.hasFeature("+MutationEvents", "1.0"));
//			logger.debug("MutationEvents DOM Level 2: " + domImpl.hasFeature("+MutationEvents", "2.0"));
//			logger.debug("MutationEvents DOM Level 3: " + domImpl.hasFeature("+MutationEvents", "3.0"));
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
				DomDecoder.decodeOne(
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
	
	private String clientNodeIdentifier(org.w3c.dom.Node node) {
		String varName = node2varName(node);
		if (varName != null)
			return varName;
		if (node instanceof Element) {
			Element element = (Element) node;
			NamedNodeMap attrs = element.getAttributes();
			for (int attrIndex = 0; attrIndex < attrs.getLength(); attrIndex++ ) {
				Attr attr = (Attr) attrs.item(attrIndex);
				if (attr.isId() 
						|| attr.getName().equalsIgnoreCase("id") // TODO: delete this two lines of workaround and find better way to manage id attrs
						|| attr.getName().equals("xml:id") )
					return "document.getElementById('" + attr.getValue() + "')";
			}
		}
		org.w3c.dom.Node parent = node.getParentNode();
		if (parent instanceof Document)
			return "document.documentElement";
		int childIndex = 0;
		for (org.w3c.dom.Node currNode = node; currNode != parent.getFirstChild(); currNode = currNode.getPreviousSibling())
			childIndex++;
		return clientNodeIdentifier(parent) + ".childNodes[" + childIndex + "]";
	}
	
	Map<org.w3c.dom.Node, Set<String>> listenedNodeAndTypes = new HashMap<org.w3c.dom.Node, Set<String>>();

	private String genAddEventListener(
			org.w3c.dom.Node target,
			String type,
			boolean useCapture) {
		if (target instanceof Element) {
			return clientNodeIdentifier(target)
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

	private void addAttrModify(MutationEvent event) {
		if (!docLoadedOnClient)
			return;
		String elemId = clientNodeIdentifier((org.w3c.dom.Node) event.getTarget());
		String nsURI = event.getRelatedNode().getNamespaceURI();
		String cmd = null;
		switch(event.getAttrChange()) {
			case MutationEvent.ADDITION :
			case MutationEvent.MODIFICATION :
				if (nsURI != null)
					cmd = elemId + ".setAttributeNS('" + nsURI + "','" + event.getAttrName() + "','" + stringEncode(event.getNewValue()) + "')";
				else
					cmd = elemId + ".setAttribute('" + event.getAttrName() + "','" + stringEncode(event.getNewValue()) + "')";
				break;
			case MutationEvent.REMOVAL :
				if (nsURI != null)
					cmd = elemId + ".removeAttributeNS('" + nsURI + "','" + event.getAttrName() + "')";
				else
					cmd = elemId + ".removeAttribute('" + event.getAttrName() + "')";
				break;
		}
		if (cmd != null)
			addClientCommand( cmd );
	}
	
	private void addCharacterDataModify(MutationEvent event) {
		if (!docLoadedOnClient)
			return;
		String elemId = clientNodeIdentifier((org.w3c.dom.Node) event.getTarget());
		String cmd = null;
		cmd = elemId + ".nodeValue = '" + stringEncode(((MutationEvent) event).getNewValue()) + "'";
		if (cmd != null)
			addClientCommand( cmd );
	}
	
	private void addNodeCreation(MutationEvent event) {
		if (!docLoadedOnClient)
			return;
		org.w3c.dom.Node newNode = (org.w3c.dom.Node) event.getTarget();
		String nsURI = newNode.getNamespaceURI();
		String cmd = null;
		switch(newNode.getNodeType()) {
			case(org.w3c.dom.Node.ATTRIBUTE_NODE) :
				if (nsURI != null)
					cmd = "var " + newNode2varName(newNode) + " = document.createAttributeNS('" + nsURI + "','" + newNode.getNodeName() + "')";
				else
					cmd = "var " + newNode2varName(newNode) + " = document.createAttribute('" + newNode.getNodeName() + "')";
				break;
			case(org.w3c.dom.Node.ELEMENT_NODE) :
				if (nsURI != null)
					cmd = "var " + newNode2varName(newNode) + " = document.createElementNS('" + nsURI + "','" + newNode.getNodeName() + "')";
				else
					cmd = "var " + newNode2varName(newNode) + " = document.createElement('" + newNode.getNodeName() + "')";
				break;
			case(org.w3c.dom.Node.TEXT_NODE) :
				cmd = "var " + newNode2varName(newNode) + " = document.createText('" + stringEncode(newNode.getNodeValue()) + "')";
				break;
			case(org.w3c.dom.Node.DOCUMENT_FRAGMENT_NODE) :
				cmd = "var " + newNode2varName(newNode) + " = document.createDocumentFragment()";
				break;
			case(org.w3c.dom.Node.COMMENT_NODE) :
				cmd = "var " + newNode2varName(newNode) + " = document.createComment('" + stringEncode(newNode.getNodeValue()) + "')";
				break;
		}
		if (cmd != null)
			addClientCommand( cmd );
	}
	
	private String stringEncode(String inputString) {
		return inputString.replace("'", "\\'");
	}

	private String addCompleteNodeCreation(org.w3c.dom.Node newNode) {
		String varName = node2varName(newNode);
		if (varName != null)
			return varName;
		String nsURI = newNode.getNamespaceURI();
		String cmd = null;
		varName = newNode2varName(newNode);
		switch(newNode.getNodeType()) {
			case(org.w3c.dom.Node.ATTRIBUTE_NODE) :
				if (nsURI != null)
					cmd = "var " + varName + " = document.createAttributeNS('" + nsURI + "','" + newNode.getNodeName() + "')";
				else
					cmd = "var " + varName + " = document.createAttribute('" + newNode.getNodeName() + "')";
				break;
			case(org.w3c.dom.Node.ELEMENT_NODE) :
				if (nsURI != null)
					cmd = "var " + varName + " = document.createElementNS('" + nsURI + "','" + newNode.getNodeName() + "')";
				else
					cmd = "var " + varName + " = document.createElement('" + newNode.getNodeName() + "')";
				NamedNodeMap attrMap = newNode.getAttributes();
				for (int attrIndex = 0; attrIndex < attrMap.getLength(); attrIndex++) {
					Attr attr = (Attr) attrMap.item(attrIndex);
					String attrNsURI = attr.getNamespaceURI();
					if (attrNsURI != null)
						cmd += "; " + varName + ".setAttributeNS('" + attrNsURI + "','" + attr.getName() + "','" + stringEncode(attr.getValue()) + "')";
					else
						cmd += "; " + varName + ".setAttribute('" + attr.getName() + "','" + stringEncode(attr.getValue()) + "')";
				}
				NodeList children = newNode.getChildNodes();
				for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
					org.w3c.dom.Node child = children.item(childIndex);
					cmd += "; " + varName + ".appendChild(" + addCompleteNodeCreation(child) + ")";
				}
//				NodeList children = newNode.getChildNodes();
//				for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
//					
//				}
				break;
			case(org.w3c.dom.Node.TEXT_NODE) :
				cmd = "var " + varName + " = document.createTextNode('" + stringEncode(newNode.getNodeValue()) + "')";
				break;
			case(org.w3c.dom.Node.DOCUMENT_FRAGMENT_NODE) :
				cmd = "var " + varName + " = document.createDocumentFragment()";
				break;
			case(org.w3c.dom.Node.COMMENT_NODE) :
				cmd = "var " + varName + " = document.createComment('" + stringEncode(newNode.getNodeValue()) + "')";
				break;
		}
		if (cmd != null)
			addClientCommand( cmd );
		return varName;
	}

	private void addNodeRemovalFromDoc(MutationEvent event) {
	}

	private void addNodeInsert(MutationEvent event) {
		if (!docLoadedOnClient)
			return;
		org.w3c.dom.Node node = (org.w3c.dom.Node) event.getTarget();
		org.w3c.dom.Node parentNode = (org.w3c.dom.Node) event.getRelatedNode();
		org.w3c.dom.Node nextSibling = node.getNextSibling();
		addClientCommand(
				clientNodeIdentifier(parentNode)
				+ ( (nextSibling != null) ?
						".insertBefore(" + addCompleteNodeCreation(node) + "," + clientNodeIdentifier(nextSibling) + ")" :
						".appendChild(" + addCompleteNodeCreation(node) + ")" ) );
	}

	private void addNodeRemoval(MutationEvent event) {
		if (!docLoadedOnClient)
			return;
		org.w3c.dom.Node childNode = (org.w3c.dom.Node) event.getTarget();
		org.w3c.dom.Node parentNode = (org.w3c.dom.Node) event.getRelatedNode();
		addClientCommand(clientNodeIdentifier(parentNode) + ".removeChild(" + clientNodeIdentifier(childNode) + ")");
	}

	public void removeEventListener(
			Node targetNode, org.w3c.dom.Node target, String type,
			EventListener listener, boolean useCapture) {
		Set<String> listenedTypesForTarget = listenedNodeAndTypes.get(target);
		if (listenedTypesForTarget != null) {
			listenedTypesForTarget.remove(type);
			if (listenedTypesForTarget.isEmpty())
				listenedNodeAndTypes.remove(target);
		}
//		if (target instanceof Element) {
//			((Element) target).removeAttribute("on" + type);
			if (docLoadedOnClient)
				addClientCommand(
						clientNodeIdentifier(target)
								+ ".removeEventListener( '"
								+ type + "', "
								+ JS_CALLBACK_FUNCTION_NAME + ", "
								+ useCapture + " )");
//		}
	}
	
	private String getContentType() {
		Element docElem = document.getDocumentElement();
		if ( docElem.getNodeName().equals("html") || docElem.getLocalName().equals("html") )
			return "text/html";
		if ( docElem.getNodeName().equals("svg") || docElem.getLocalName().equals("svg") )
			return "image/svg+xml";
		return "application/xml";
	}
	
	private void sendEntireDocument(HttpServletResponse response) throws IOException {
		response.setContentType(getContentType());
//		response.setContentType("text/html");
	    OutputStream out = response.getOutputStream();
      	DOMImplementationLS feature = (DOMImplementationLS) domImpl.getFeature("LS",
        		"3.0");
        LSSerializer serializer = feature.createLSSerializer();
        LSOutput output = feature.createLSOutput();
        output.setByteStream(out);
        serializer.write(document, output);
        docLoadedOnClient = true;
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
	    	resetCommandSet();
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
