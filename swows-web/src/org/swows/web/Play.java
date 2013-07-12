package org.swows.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.swows.datatypes.SmartFileManager;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.DatasetFactory;

/**
 * Servlet implementation class InitialContentServlet
 */
@WebServlet("/play")
public class Play extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private WebApp webApp = null;

    /**
     * Default constructor. 
     */
    public Play() {
    }
    
    private WebApp getWebApp(HttpServletRequest request) {
    	if (webApp == null) {
    		String dfUri = request.getParameter("dataflow");
    		if (dfUri == null) dfUri = request.getParameter("df");
//    		Document resultDoc = MouseApp.createContent(domImpl, dfUri);
    		Dataset dfDataset = DatasetFactory.create(dfUri, SmartFileManager.get());
    		final Graph dfGraph = dfDataset.asDatasetGraph().getDefaultGraph();
    		webApp = new WebApp(dfGraph);
    	}
    	return webApp;
    }
    
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		WebApp webApp = getWebApp(request);
//		Document resultDoc = webApp.getDocument();
		webApp.doGet(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		WebApp webApp = getWebApp(request);
		webApp.doPost(request, response);
	}

}
