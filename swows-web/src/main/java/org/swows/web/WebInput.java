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

import java.io.StringReader;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.swows.graph.events.DynamicGraph;
import org.swows.graph.events.DynamicGraphFromGraph;
import org.swows.runnable.LocalTimer;
import org.swows.runnable.RunnableContextFactory;
import org.swows.vocabulary.DOMEvents;
import org.swows.xmlinrdf.DomEventListener;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import com.hp.hpl.jena.graph.GraphMaker;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.impl.SimpleGraphMaker;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFReader;
import com.hp.hpl.jena.rdf.model.RDFReaderF;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.rdf.model.impl.RDFReaderFImpl;
import com.hp.hpl.jena.vocabulary.RDF;

public class WebInput implements DomEventListener {

	private static final String DEFAULT_BASE = "";

	private static GraphMaker graphMaker = new SimpleGraphMaker(); 

    private DynamicGraphFromGraph eventGraph;
    
//    private boolean isReceiving = false;
//    private Logger logger = Logger.getLogger(getClass());
//    
//    private RunnableContext runnableContext = null;
    
//    private Map<MouseEvent,Set<Node>> event2domNodes = new HashMap<MouseEvent, Set<Node>>();
    
	private static final Logger logger = Logger.getLogger(WebInput.class);
	
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
	
	private static class EventWithDescriptor implements Event {
		
//		private String descriptor;
//		private Model model;
		private Resource eventResource;
		
		public EventWithDescriptor(Resource eventResource) {
			this.eventResource = eventResource;
		}
		
		public boolean getBubbles() {
			// TODO Auto-generated method stub
			return false;
		}

		public boolean getCancelable() {
			// TODO Auto-generated method stub
			return false;
		}

		public EventTarget getCurrentTarget() {
			// TODO Auto-generated method stub
			return null;
		}

		public short getEventPhase() {
			// TODO Auto-generated method stub
			return 0;
		}

		public EventTarget getTarget() {
			// TODO Auto-generated method stub
			eventResource.getRequiredProperty(DOMEvents.target);
			return null;
		}

		public long getTimeStamp() {
			// TODO Auto-generated method stub
			return 0;
		}

		public String getType() {
			// TODO Auto-generated method stub
			return null;
		}

		public void initEvent(String arg0, boolean arg1, boolean arg2) {
			// TODO Auto-generated method stub
			
		}

		public void preventDefault() {
			// TODO Auto-generated method stub
			
		}

		public void stopPropagation() {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	public static void handleEvent(String eventString, EventListener listener) {
		RDFReaderF readerFactory = new RDFReaderFImpl();
		RDFReader reader = readerFactory.getReader("N3");
		Model model = ModelFactory.createDefaultModel();
		reader.read(
				model,
				new StringReader(eventString),
				DEFAULT_BASE);
		ResIterator eventResources = model.listResourcesWithProperty(RDF.type, DOMEvents.Event);
		while(eventResources.hasNext()) {
			listener.handleEvent(new EventWithDescriptor(eventResources.next()));
		}
	}
	
	public void handleEvent(String eventString) {
		buildGraph();
		RDFReaderF readerFactory = new RDFReaderFImpl();
		RDFReader reader = readerFactory.getReader("N3");
		Model model = ModelFactory.createModelForGraph(eventGraph);
		reader.read(
				model,
				new StringReader(eventString),
				DEFAULT_BASE);
		logger.debug("Launching update thread... ");
//		LocalTimer.get().schedule(
//				new TimerTask() {
//			//					public void run() {
						RunnableContextFactory.getDefaultRunnableContext().run(
								new Runnable() {
									public void run() {
										logger.debug("Sending update events ... ");
										eventGraph.sendUpdateEvents();
										logger.debug("Update events sent!");
									}
								} );
//					}
//				}, 0 );
//		logger.debug("Update thread launched!");
	}
	
	public synchronized void handleEvent(Event event, Node currentTargetGraphNode , Node targetGraphNode) {
		Resource eventResource = ((EventWithDescriptor) event).eventResource;
		buildGraph();
		StmtIterator closure = eventResource.listProperties();
		while(closure.hasNext()) {
			eventGraph.add(closure.nextStatement().asTriple());
		}
		logger.debug("Launching update thread... ");
		LocalTimer.get().schedule(
				new TimerTask() {
					public void run() {
						RunnableContextFactory.getDefaultRunnableContext().run(
								new Runnable() {
									public void run() {
										logger.debug("Sending update events ... ");
										eventGraph.sendUpdateEvents();
										logger.debug("Update events sent!");
									}
								} );
					}
				}, 0 );
		logger.debug("Update thread launched!");
	}

}
