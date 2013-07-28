package org.swows.web;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.jena.riot.RDFDataMgr;

/**
 * Servlet implementation class InitialContentServlet
 */
@WebServlet("/play")
public class Play extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Map<String,Map<Long,WebApp>> webAppMap = null;
	private Map<String,Long> instCount = null;
	
	private static final String WEB_APP_MAPP_ATTR_NAME = "org.swows.web.webAppMap";
	private static final String INST_COUNT_ATTR_NAME = "org.swows.web.instCount";

    /**
     * Default constructor. 
     */
    public Play() {
    }
    
    @SuppressWarnings("unchecked")
	private synchronized WebApp getWebApp(HttpSession session, String dataflowUri, long instanceId, StringBuffer requestURL) {
    	if (webAppMap == null) {
    		webAppMap = (Map<String,Map<Long,WebApp>>) session.getAttribute(WEB_APP_MAPP_ATTR_NAME);
    		instCount = (Map<String,Long>) session.getAttribute(INST_COUNT_ATTR_NAME);
    		if (webAppMap == null) {
    			webAppMap = new HashMap<>();
    			instCount = new HashMap<>();
    			session.setAttribute(WEB_APP_MAPP_ATTR_NAME, webAppMap);
    			session.setAttribute(INST_COUNT_ATTR_NAME, instCount);
    		}
    	}
    	Map<Long,WebApp> mapForUri = webAppMap.get(dataflowUri);
   		Long countForUri = instCount.get(dataflowUri);
    	if (mapForUri == null) {
    		mapForUri = new HashMap<>();
    		countForUri = 0l;
     		webAppMap.put(dataflowUri, mapForUri);
     		instCount.put(dataflowUri, countForUri);
    	}
    	WebApp webApp = null;
//    	String newQueryString = queryString;
    	if (instanceId >= 0)
    		webApp = mapForUri.get(instanceId);
    	else {
    		instanceId = countForUri.longValue();
    		countForUri++;
//    		newQueryString += ("&inst=" + Long.toString(instanceId));
    		requestURL.append("&inst=").append(Long.toString(instanceId));
    	}
    	if (webApp == null) {
    		webApp = new WebApp(RDFDataMgr.loadGraph(dataflowUri),requestURL.toString());
    		mapForUri.put(instanceId, webApp);
    	}
    	return webApp;
    }
    
    private WebApp getWebApp(HttpServletRequest request) {
   		String dfUri = request.getParameter("dataflow");
   		if (dfUri == null)
   			dfUri = request.getParameter("df");
   		String instanceIdStr = request.getParameter("instanceId");
   		if (instanceIdStr == null)
   			instanceIdStr = request.getParameter("inst");
   		long instanceId = -1;
//   		StringBuffer requestURL = request.getRequestURL();
   		if (instanceIdStr != null)
    		instanceId = Long.parseLong(instanceIdStr);
//   		else
//   			requestURL.append("&id=" + Long.toString(instanceId));
    	return getWebApp(
    			request.getSession(),
    			dfUri,
    			instanceId,
    			request.getRequestURL().append("?").append(request.getQueryString()) );
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
