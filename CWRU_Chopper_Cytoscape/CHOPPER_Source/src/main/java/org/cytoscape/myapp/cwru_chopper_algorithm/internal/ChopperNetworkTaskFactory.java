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

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.myapp.cwru_chopper_algorithm.internal.ChopperNetworkTask;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.task.NetworkTaskFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.TaskIterator;

public class ChopperNetworkTaskFactory implements NetworkTaskFactory {
	
	private final CyNetworkManager netMgr;
	private final CyNetworkFactory cnf;
	private final CyNetworkNaming namingUtil;
	private CyNetworkView viewNetwork;
	private CyNetworkViewFactory viewFactory;
	private CyNetworkViewManager viewManager;
	
	public ChopperNetworkTaskFactory(CyNetwork cN, 
									final CyNetworkManager netMgr, 
									final CyNetworkNaming namingUtil,
									final CyNetworkFactory cnf,
									CyNetworkView viewNetwork,
									CyNetworkViewFactory viewFactory,
									CyNetworkViewManager viewManager){
		
		this.netMgr = netMgr;
		this.namingUtil = namingUtil;
		this.cnf = cnf;
		this.viewNetwork = viewNetwork;
		this.viewFactory = viewFactory;
		this.viewManager = viewManager;
	}
	
	@Override
	public TaskIterator createTaskIterator(CyNetwork network) {
		return new TaskIterator(new ChopperNetworkTask(network, netMgr, cnf, namingUtil, viewNetwork, viewFactory, viewManager));
	}

	@Override
	public boolean isReady(CyNetwork network) {
		return network != null;
	}
	
}
