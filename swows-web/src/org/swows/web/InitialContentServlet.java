package org.swows.web;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.swows.datatypes.SmartFileManager;
import org.swows.graph.DynamicDatasetMap;
import org.swows.graph.events.DynamicGraph;
import org.swows.graph.events.DynamicGraphFromGraph;
import org.swows.mouse.MouseApp;
import org.swows.mouse.MouseInput;
import org.swows.producer.DataflowProducer;
import org.swows.time.SystemTime;
import org.swows.vocabulary.SWI;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.DatasetFactory;

/**
 * Servlet implementation class InitialContentServlet
 */
@WebServlet("/InitialContentServlet")
public class InitialContentServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

    /**
     * Default constructor. 
     */
    public InitialContentServlet() {
        // TODO Auto-generated constructor stub
    }
    
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String dfUri = request.getParameter("dataflow");
		DOMImplementation domImpl;
		try {
			domImpl = DOMImplementationRegistry.newInstance().getDOMImplementation("XML 3.0");
		} catch (ClassNotFoundException e) {
			throw new ServletException(e);
		} catch (InstantiationException e) {
			throw new ServletException(e);
		} catch (IllegalAccessException e) {
			throw new ServletException(e);
		} catch (ClassCastException e) {
			throw new ServletException(e);
		}
//		Document resultDoc = MouseApp.createContent(domImpl, dfUri);
		Dataset dfDataset = DatasetFactory.create(dfUri, SmartFileManager.get());
		final Graph dfGraph = dfDataset.asDatasetGraph().getDefaultGraph();
		WebApp webApp = new WebApp(dfGraph);
		Document resultDoc = webApp.getDocument();
		
		response.setContentType("image/svg+xml");
//		response.setContentType("text/html");
	    OutputStream out = response.getOutputStream();
      	DOMImplementationLS feature = (DOMImplementationLS) domImpl.getFeature("LS",
        		"3.0");
        LSSerializer serializer = feature.createLSSerializer();
        LSOutput output = feature.createLSOutput();
        output.setByteStream(out);
        serializer.write(resultDoc, output);

	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
	}

}
