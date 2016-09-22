/*
 * 
 * *****************************************************************************************
 * Author: Ugurcan Aytar
 * Senior Computer Science Student at Bilkent University - Ankara, Turkey
 * Research Intern at Case Western Reserve University - Cleveland, OH, USA
 * September, 2016
 * 
 * Advisor: Prof. Mehmet Koyuturk
 * Supervisor: Mustafa Coskun, PhD
 * 
 * 
 * 			Cytoscape Implementation of "Efficient Processing of Network 
 * 			  Proximity Queries via Chebyshev Acceleration" — CHOPPER
 * 
 * For your questions, please feel free to e-mail me: ugurcan.aytar@ug.bilkent.edu.tr
 * 
 * 
 * *****************************************************************************************
 * 
 */

package org.cytoscape.myapp.cwru_chopper_algorithm.internal;

import java.util.Properties;

import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.myapp.cwru_chopper_algorithm.internal.ChopperNetworkTaskFactory;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.task.NetworkTaskFactory;
import org.cytoscape.work.ServiceProperties;
import org.osgi.framework.BundleContext;

public class CyActivator extends AbstractCyActivator {
	
	public CyActivator() { }
	
	@Override
	public void start(BundleContext context) {
		
		CyNetworkManager cyNM = getService(context, CyNetworkManager.class);
		CyNetworkNaming cyNN = getService(context, CyNetworkNaming.class);
		CyNetworkFactory cyNF = getService(context, CyNetworkFactory.class);
		
		/*
		CyApplicationManager cyAM = getService(context,CyApplicationManager.class);
		VisualMappingManager cyVM = getService(context,VisualMappingManager.class);
		VisualStyleFactory cyVF = getService(context,VisualStyleFactory.class);
		VisualMappingFunctionFactory cyFF = getService(context,VisualMappingFunctionFactory.class, "(mapping.type=discrete)");
		*/
		
		Properties properties = new Properties();
		properties.put(ServiceProperties.PREFERRED_MENU, ServiceProperties.APPS_MENU);
		properties.put(ServiceProperties.TITLE, "CWRU ChopperAlgorithm");
		registerService(context, new ChopperNetworkTaskFactory(null, cyNM, cyNN, cyNF), NetworkTaskFactory.class, properties);
	}

}
