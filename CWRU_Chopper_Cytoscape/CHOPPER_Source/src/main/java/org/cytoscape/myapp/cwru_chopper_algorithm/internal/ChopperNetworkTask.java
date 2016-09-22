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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import cern.colt.function.DoubleDoubleFunction;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.doublealgo.Sorting;

/*
 * 
 * 				/\ PARALLEL COLT PACKAGES /\
	import cern.colt.function.tdouble.DoubleDoubleFunction;
	import cern.colt.matrix.tdouble.DoubleMatrix1D;
	import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
	import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
	import cern.colt.matrix.tdouble.algo.DoubleSorting;
 *
 *
 */
	
public class ChopperNetworkTask extends AbstractTask{
	
	/* CURRENT & OUTPUT NETWORKS */
	
	private CyNetwork network;
	private CyNetwork myNet;
	
	/* CONSTANTS */
	
	private final double alpha = 0.15;
	private final double THRESHOLD = 1.0E-10;
	private final int MAXIT = 1500;
	private final CyNetworkManager netMgr;
	private final CyNetworkFactory cnf;
	private final CyNetworkNaming namingUtil;
	
	/* IN-EQUATION PARAMETERS */
	
	private double Kappa;
	private double Xi;
	private double ErrorBound;
	private int iter;
	private double muPPrevious;
	private double muPrevious;
	private double mu;
	private int cardinalityR;
	
	/* PERFORMANCE PARAMS */
	
	private long startTime;
	private long endTime;
	private long totalTime;
	
	/* TOTAL NODE SIZE */
	
	private int sizeNetwork;
	
	/* VECTORS and ADJACENCY MATRIX */
	
	private DoubleMatrix1D resultMatHelper;
	private DoubleMatrix1D resultMat;
	private DenseDoubleMatrix1D UPPER;
	private DenseDoubleMatrix1D restartVector;
	private DenseDoubleMatrix1D mPPreviousScore;
	private DenseDoubleMatrix1D mPreviousScore;
	private DenseDoubleMatrix1D mScore;
	private DenseDoubleMatrix1D R;
	private SparseDoubleMatrix2D normalizedNetwork;
	
	/* FUNC-PASS PARAMS */
	
	private List<Integer> topKList;
	private CyNode queryNode;
	private List<CyNode> nodes;
	
	/* USER-INPUT PARAMS */
	
	@Tunable(description= "Enter Query Node -Index-: ", groups={"Query Initializer"})
	public int query = 123;
	
	@Tunable(description= "Enter K: ", groups={"Top-K Result"})
	public int K = 10;
	
	public ChopperNetworkTask(CyNetwork network, CyNetworkManager netMgr, CyNetworkFactory cnf, CyNetworkNaming namingUtil){
		
		this.network = network;
		this.netMgr = netMgr;
		this.cnf = cnf;
		this.namingUtil = namingUtil;
		
	}
	
	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		
		taskMonitor.setTitle("Computing CWRU - Chopper...");
	
		startTime = System.nanoTime();
		K += 1;
		initializeChopper(network, K, alpha, query);
		endTime = System.nanoTime();
		totalTime = endTime - startTime;
		taskMonitor.showMessage(TaskMonitor.Level.WARN, "Initializing Chopper Runtime (in sec): " + ((double)(totalTime))/1000000000);
		
		
		startTime = System.nanoTime();
		DoubleMatrix1D res = (calculateChopper().viewSorted()).viewFlip();
		endTime = System.nanoTime();
		totalTime = endTime - startTime;
		taskMonitor.showMessage(TaskMonitor.Level.WARN, "Chopper Algorithm Runtime (in sec): " + ((double)(totalTime))/1000000000);
		
		taskMonitor.setTitle(" ");
		
		topKList = new ArrayList<Integer>();
		for(int i = 0; i < K; i++){
			if(res.getQuick(i) != query+1){
				topKList.add((int) res.getQuick(i));
			}
			taskMonitor.showMessage(TaskMonitor.Level.INFO, "Result: " + res.getQuick(i));
		}
		
		taskMonitor.setTitle(" ");
		taskMonitor.showMessage(TaskMonitor.Level.INFO, "Redrawing the Top-K Network for Node: " + (query+1));
		startTime = System.nanoTime();
		//reDrawNetwork(topKList, query+1);
		efficientReDrawNetwork(topKList, query+1);
		endTime = System.nanoTime();
		totalTime = endTime - startTime;
		taskMonitor.showMessage(TaskMonitor.Level.WARN, "Redrawing Network (in sec): " + ((double)(totalTime))/1000000000);
		
		taskMonitor.setTitle(" ");
		taskMonitor.showMessage(TaskMonitor.Level.INFO, "Q-Node: Query Node (Selected Node)");
		taskMonitor.showMessage(TaskMonitor.Level.INFO, "NDC: Not Directly Connected Node to both Top-(K-1) Results and Q-Node");
	}
	
	private void initializeChopper(CyNetwork network, int K, double alpha, int query) throws FileNotFoundException, IOException {
		
		// Initialization of values, vectors and adjacency matrix.
		sizeNetwork = network.getNodeCount();
		
		normalizedNetwork = new SparseDoubleMatrix2D(sizeNetwork, sizeNetwork);
		// Reading out.txt with type [Source - Target - Column Normalized Weight] for creating adjacency matrix
		File file = new File(System.getProperty("user.dir") + "/out.txt");
		BufferedReader reader = null;
		try{
			reader = new BufferedReader(new FileReader(file));
			String text = null;
			while((text = reader.readLine()) != null){
				String[] tokens = text.split(" ");
				int row = Integer.parseInt(tokens[0])-1;
				int col = Integer.parseInt(tokens[1])-1;
				double valInd = Double.parseDouble(tokens[2]);
				if(row == col){
					// Eliminating self-referential links
					normalizedNetwork.setQuick(row, col, 0.0);
				} 
				else {
					normalizedNetwork.setQuick(row, col, valInd);
				}
			}
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
		}
		
		normalizedNetwork.trimToSize();
		
		restartVector = new DenseDoubleMatrix1D(sizeNetwork);
		restartVector.setQuick(query, 1.0);
		restartVector.trimToSize();
		
		muPPrevious = 1.0;
		muPrevious = 1 / (1 - alpha);
		mu = 0.0;
		
		/* THRESHOLD and MAXIT defined as constants above. */
		
		iter = 1;
		Kappa = (2-alpha) / alpha;
		Xi = (Math.sqrt(Kappa) - 1) / (Math.sqrt(Kappa) + 1);
		ErrorBound = Xi;
		
		
		mScore = new DenseDoubleMatrix1D(sizeNetwork);
		mScore.trimToSize();
		
		mPPreviousScore = new DenseDoubleMatrix1D(sizeNetwork);
		mPPreviousScore.setQuick(query, alpha);
		mPPreviousScore.trimToSize();
		
		mPreviousScore = new DenseDoubleMatrix1D(sizeNetwork);
		mPreviousScore.trimToSize();
		
		// initializing R Vector [1, 2, 3, 4, 5,...,sizeNetwork-1, sizeNetwork]
		R = new DenseDoubleMatrix1D(sizeNetwork);
		for(int i = 0; i < sizeNetwork; i++){
			R.setQuick(i, i+1);
		}
		R.trimToSize();	
	}
	
	private DoubleMatrix1D calculateChopper(){
		
		DoubleDoubleFunction plus = new DoubleDoubleFunction() {
		    public double apply(double a, double b) { 
		    	return a+b; 
		    }
		};
		
		DoubleDoubleFunction minus = new DoubleDoubleFunction() {
		    public double apply(double a, double b) { 
		    	return a-b; 
		    }
		};
		
		cardinalityR = R.cardinality();
		
		while(cardinalityR > K){
			
			mu = 2/(1-alpha) * muPrevious - muPPrevious;
			
			DenseDoubleMatrix1D firstMatrix = new DenseDoubleMatrix1D(sizeNetwork);
			double firstMatrixHelper = 2 * (muPrevious/mu);
			normalizedNetwork.zMult(mPreviousScore, firstMatrix);
			
			DenseDoubleMatrix1D secondMatrix = new DenseDoubleMatrix1D(sizeNetwork);
			double secondMatrixHelper = muPPrevious / mu;
			
			DenseDoubleMatrix1D thirdMatrix = new DenseDoubleMatrix1D(sizeNetwork);
			double thirdMatrixHelper = (2 * muPrevious) / ((1-alpha) * mu) * alpha;
			
			
			for(int i = 0; i < sizeNetwork; i++){
				firstMatrix.setQuick(i, firstMatrix.getQuick(i) * firstMatrixHelper);
				secondMatrix.setQuick(i, mPPreviousScore.getQuick(i) * secondMatrixHelper);
				thirdMatrix.setQuick(i, restartVector.getQuick(i) * thirdMatrixHelper);
			}

			
			DoubleMatrix1D firstRes = new DenseDoubleMatrix1D(sizeNetwork);
			DoubleMatrix1D finalVec = new DenseDoubleMatrix1D(sizeNetwork);
			firstRes = firstMatrix.assign(secondMatrix, minus);
			finalVec =  firstRes.assign(thirdMatrix, plus);
			
			mScore = new DenseDoubleMatrix1D(sizeNetwork);
			for(int i = 0; i < sizeNetwork; i++){
				mScore.setQuick(i, finalVec.getQuick(i));
			}
	
			
			// Multiply mScore equivalent's cells' by -1
			for(int i = 0; i < sizeNetwork; i++){
				finalVec.setQuick(i, finalVec.getQuick(i)*(-1));
			}
			
			double Theta = (-1) * findkTHValue(finalVec, K);
			
			reCreateUPPER();
			updateR(Theta);
						
			cardinalityR = R.cardinality();
			iter = iter + 1;
			ErrorBound = ErrorBound * Xi;
			muPPrevious = muPrevious;
			muPrevious = mu;
	        mPPreviousScore = mPreviousScore;
	        mPreviousScore = mScore;
			
			if (iter == MAXIT || ErrorBound < THRESHOLD){
				break;
			}
		}
		
		resultMatHelper = new DenseDoubleMatrix1D(sizeNetwork);
		resultMat = new DenseDoubleMatrix1D(sizeNetwork);
		
		if(cardinalityR > K){
			moreThanKValues(mScore);
		} 
		else {
			lessThanKValues(R);
		}
		
		return resultMat;
	}

	private void reCreateUPPER(){
		
		UPPER = new DenseDoubleMatrix1D(sizeNetwork);
		int m = 0;
		for(int i = 0; i < sizeNetwork; i++){
			if(R.getQuick(i) != 0){
				UPPER.setQuick(m++, mScore.getQuick((int)(R.getQuick(i)-1)) + 4 * ErrorBound);
			} 
			else {
				UPPER.setQuick(m++, 0);
			}
		}	
	}
		
	private void updateR(double Theta){
		
		for(int i = 0; i < sizeNetwork; i++){
			if(UPPER.getQuick(i) >= Theta){
				continue;
			} 
			else {
				R.setQuick(i, 0);
			}
		}
	}
	
	private void moreThanKValues(DenseDoubleMatrix1D mScore){
		
		resultMatHelper = Sorting.quickSort.sort(mScore);
		/*
		 * 			Sorting at Parallel Colt
		 * resultMatHelper = DoubleSorting.mergeSort.sort(mScore2);
		 * 
		 */
		resultMatHelper = resultMatHelper.viewFlip();
		for(int i = 0; i <= K; i++){
			resultMat.setQuick(i, resultMatHelper.getQuick(i));
		}
	}
	
	private void lessThanKValues(DenseDoubleMatrix1D R) {
		
		int f = 0;
		for (int i = 0; i < sizeNetwork; i++){
			if (R.getQuick(i) != 0){
				resultMat.setQuick(f++, R.getQuick(i));
			}
		}
		
	}
	
	private double findkTHValue(DoubleMatrix1D finalVec, final int kVal){
		
		DoubleMatrix1D sortedVec = new DenseDoubleMatrix1D(sizeNetwork);
		sortedVec = Sorting.quickSort.sort(finalVec);
		/*
		 * 			Sorting at Parallel Colt
		 * sortedVec = DoubleSorting.mergeSort.sort(finalVec);
		 * 
		 */
		double kTH = sortedVec.getQuick(kVal-1);
		
		return kTH;
	}
	
	@SuppressWarnings("unused")
	private void reDrawNetwork(final List<Integer> resultList, final int query){
		
		CyTable nodeTable = network.getDefaultNodeTable();
		List<CyNode> nodes = new ArrayList<CyNode>();
		
		for(CyNode node: network.getNodeList()){
			if (resultList.contains(Integer.parseInt(network.getRow(node).get(CyNetwork.NAME, String.class)))){
				nodes.add(node);
			} 
			else if (query == Integer.parseInt(network.getRow(node).get(CyNetwork.NAME, String.class))){
				queryNode = node;
			}
		}
		
		for(CyNode node: network.getNodeList()){
			if(nodes.contains(node) || node == queryNode){
				continue;
			} 
			else {
				CyNode _node = node;
				CyRow row = nodeTable.getRow(_node.getSUID());
				nodeTable.deleteRows(Collections.singletonList(row.getRaw("SUID")));
				network.removeNodes(Collections.singletonList(_node));
			}
		}
		
		CyTable edgeTable = network.getDefaultEdgeTable();
		List<CyEdge> edgeList = network.getEdgeList();
		for(CyEdge edge: edgeList){
			if(edge.getSource() == queryNode || edge.getTarget() == queryNode || nodes.contains(edge.getTarget()) || nodes.contains(edge.getSource())){
				continue;
			} 
			else {
				CyEdge _edge = edge;
				CyRow row = edgeTable.getRow(_edge.getSUID());
				edgeTable.deleteRows(Collections.singletonList(row.getRaw("SUID")));
				network.removeEdges(Collections.singletonList(edge));
			}
		}
	}
	
	private void efficientReDrawNetwork(final List<Integer> resultList, final int query){
		
		nodes = new ArrayList<CyNode>();
		ListMultimap<String, String> nodeMap = ArrayListMultimap.create();
		List<String> nodeNames = new ArrayList<String>();
		String queryName = "";
		for(CyNode node: network.getNodeList()){
			if (resultList.contains(Integer.parseInt(network.getRow(node).get(CyNetwork.NAME, String.class)))){
				nodes.add(node);
				nodeNames.add(network.getRow(node).get(CyNetwork.NAME, String.class));
			} 
			else if (query == Integer.parseInt(network.getRow(node).get(CyNetwork.NAME, String.class))){
				queryNode = node;
				queryName = (network.getRow(node).get(CyNetwork.NAME, String.class));
			}
		}
		
		for(CyEdge edge: network.getEdgeList()){
			if(nodes.contains(edge.getSource()) && nodes.contains(edge.getTarget())){
				nodeMap.put(network.getRow(edge.getSource()).get(CyNetwork.NAME, String.class), network.getRow(edge.getTarget()).get(CyNetwork.NAME, String.class));
			} 
			else if(queryNode == edge.getSource() && nodes.contains(edge.getTarget())){
				nodeMap.put(network.getRow(queryNode).get(CyNetwork.NAME, String.class), network.getRow(edge.getTarget()).get(CyNetwork.NAME, String.class));
			} 
			else if(nodes.contains(edge.getSource()) && queryNode == edge.getTarget()){
				nodeMap.put(network.getRow(edge.getSource()).get(CyNetwork.NAME, String.class), network.getRow(queryNode).get(CyNetwork.NAME, String.class));
			}
		}
		
		
		myNet = cnf.createNetwork();
		myNet.getRow(myNet).set(CyNetwork.NAME, namingUtil.getSuggestedNetworkTitle("Result Network"));
		
		
		int j = 0;
		for(CyNode node: nodes){
			node = myNet.addNode();
			myNet.getDefaultNodeTable().getRow(node.getSUID()).set("name", nodeNames.get(j++));
		}
		
		queryNode = myNet.addNode();
		myNet.getDefaultNodeTable().getRow(queryNode.getSUID()).set("name", queryName);

		for(CyNode n: myNet.getNodeList()){
			List<String> map = nodeMap.get(myNet.getRow(n).get(CyNetwork.NAME, String.class));
			for(CyNode n2: myNet.getNodeList()){
				if (map.contains(myNet.getRow(n2).get(CyNetwork.NAME, String.class))){
					myNet.addEdge(n, n2, false);
				}
			}
		}
		
		for(CyNode n : myNet.getNodeList()){
			if(myNet.getNeighborList(n, CyEdge.Type.ANY).size() > 0){
				continue;
			} else {
				myNet.getDefaultNodeTable().getRow(n.getSUID()).set("name", myNet.getRow(n).get(CyNetwork.NAME, String.class) + " (NDC)");
				myNet.addEdge(n, queryNode, false);
			}
		}
		
		myNet.getDefaultNodeTable().getRow(queryNode.getSUID()).set("name", "Q-Node: " + queryName);
		netMgr.addNetwork(myNet);
	}
}
