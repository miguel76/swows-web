/*
 * Copyright (c) 2011 Miguel Ceriani
 * miguel.ceriani@gmail.com

 * This file is part of Semantic Web Open datatafloW System (SWOWS).

 * SWOWS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.

 * SWOWS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General
 * Public License along with SWOWS.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.swows.web;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.swows.graph.events.DynamicGraph;
import org.swows.graph.events.DynamicGraphFromGraph;
import org.swows.runnable.LocalTimer;
import org.swows.runnable.RunnableContextFactory;
import org.swows.util.GraphUtils;
import org.swows.vocabulary.DOMEvents;
import org.swows.xmlinrdf.DomEventListener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.MouseEvent;

import com.hp.hpl.jena.graph.GraphMaker;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.graph.impl.SimpleGraphMaker;
import com.hp.hpl.jena.sparql.graph.GraphFactory;
import com.hp.hpl.jena.vocabulary.RDF;

public class WebInput {

	private static GraphMaker graphMaker = new SimpleGraphMaker(); 

    private DynamicGraphFromGraph eventGraph;
    
//    private boolean isReceiving = false;
//    private Logger logger = Logger.getLogger(getClass());
//    
//    private RunnableContext runnableContext = null;
    
//    private Map<MouseEvent,Set<Node>> event2domNodes = new HashMap<MouseEvent, Set<Node>>();
    
	private Logger logger = Logger.getRootLogger();
	
//    private TimerTask localTimerTask = new TimerTask() {
//		@Override
//		public void run() {
//			logger.debug("Sending update events ... ");
//			mouseEventGraph.sendUpdateEvents();
//			logger.debug("Update events sent!");
//		}
//	};
    
//    private void startReceiving() {
//    	isReceiving = true;
//    }
//    
//    private void stopReceiving() {
//    	if (isReceiving) {
//    		RunnableContextFactory.getDefaultRunnableContext().run(localTimerTask);
////    		if (runnableContext != null)
////    			runnableContext.run(localTimerTask);
////    		else
////    			LocalTimer.get().schedule(localTimerTask, 0);
//    	}
//    	isReceiving = false;
//    }
//    

	public void buildGraph() {
		if (eventGraph == null) {
			eventGraph = new DynamicGraphFromGraph( graphMaker.createGraph() );
		} else {
			eventGraph.clear();
		}
	}
	
	public synchronized DynamicGraph getGraph() {
		buildGraph();
		return eventGraph;
	}
	
	public synchronized void handleEvent(String eventString) {
		buildGraph();
		
//		GraphFactory.

			Node eventNode = Node.createURI(DOMEvents.getInstanceURI() + "event_" + event.hashCode());
			eventGraph.add( new Triple( eventNode, RDF.type.asNode(), DOMEvents.Event.asNode() ) );
			eventGraph.add( new Triple( eventNode, RDF.type.asNode(), DOMEvents.UIEvent.asNode() ) );
			eventGraph.add( new Triple( eventNode, RDF.type.asNode(), DOMEvents.MouseEvent.asNode() ) );

			eventGraph.add( new Triple( eventNode, DOMEvents.currentTarget.asNode(), currentTargetGraphNode ));
			eventGraph.add( new Triple( eventNode, DOMEvents.target.asNode(), targetGraphNode ));
//			for (Node targetNode : domNodes)
//				mouseEventGraph.add( new Triple( eventNode, DOMEvents.target.asNode(), targetNode ));

			GraphUtils.addIntegerProperty(
					eventGraph, eventNode,
					DOMEvents.timeStamp.asNode(), event.getTimeStamp());
			
			GraphUtils.addIntegerProperty(
					eventGraph, eventNode,
					DOMEvents.detail.asNode(), mouseEvent.getDetail());
			
//		    public static final Property target = property( "target" );
//		    public static final Property currentTarget = property( "currentTarget" );

//		    public static final Property button = property( "button" );
//		    public static final Property relatedTarget = property( "relatedTarget" );
			
			GraphUtils.addDecimalProperty(
					eventGraph, eventNode,
					DOMEvents.screenX.asNode(), mouseEvent.getScreenX());
			GraphUtils.addDecimalProperty(
					eventGraph, eventNode,
					DOMEvents.screenY.asNode(), mouseEvent.getScreenY());
			GraphUtils.addDecimalProperty(
					eventGraph, eventNode,
					DOMEvents.clientX.asNode(), mouseEvent.getClientX());
			GraphUtils.addDecimalProperty(
					eventGraph, eventNode,
					DOMEvents.clientY.asNode(), mouseEvent.getClientY());

			GraphUtils.addBooleanProperty(
					eventGraph, eventNode,
					DOMEvents.ctrlKey.asNode(), mouseEvent.getCtrlKey());
			GraphUtils.addBooleanProperty(
					eventGraph, eventNode,
					DOMEvents.shiftKey.asNode(), mouseEvent.getShiftKey());
			GraphUtils.addBooleanProperty(
					eventGraph, eventNode,
					DOMEvents.altKey.asNode(), mouseEvent.getAltKey());
			GraphUtils.addBooleanProperty(
					eventGraph, eventNode,
					DOMEvents.metaKey.asNode(), mouseEvent.getMetaKey());

			GraphUtils.addIntegerProperty(
					eventGraph, eventNode,
					DOMEvents.button.asNode(), mouseEvent.getButton());

			logger.debug("Launching update thread... ");
			LocalTimer.get().schedule(
					new TimerTask() {
						@Override
						public void run() {
							RunnableContextFactory.getDefaultRunnableContext().run(
									new Runnable() {
										@Override
										public void run() {
											logger.debug("Sending update events ... ");
											eventGraph.sendUpdateEvents();
											logger.debug("Update events sent!");
										}
									} );
						}
					}, 0 );
			logger.debug("Update thread launched!");
//		}
	}

}
