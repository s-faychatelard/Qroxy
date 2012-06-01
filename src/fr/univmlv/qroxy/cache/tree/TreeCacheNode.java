package fr.univmlv.qroxy.cache.tree;

import java.util.HashMap;

public class TreeCacheNode {
	private final HashMap<String, TreeCacheNode> child = new HashMap<String, TreeCacheNode>();
	private int weight = 0;
	
	public void addChild(String[] paths, int index){
		String s = paths[index];
		this.weight++;
		if (!child.containsKey(s)) {
			child.put(s, new TreeCacheNode());
		}
		if (index+1 < paths.length)
			child.get(s).addChild(paths, index+1);
	}
	
	public String getSmallerWeightPath() {
		Object[] array = (Object[])child.keySet().toArray();
		if (array.length == 0)
			return "";		
		
		int smallerIndex = 0;
		int smallerWeight = child.get(array[smallerIndex]).getWeight();
		for(int i=0; i<array.length; i++) {
			if (child.get(array[i]).getWeight() < smallerWeight) {
				smallerIndex=i;
				smallerWeight = child.get(array[smallerIndex]).getWeight();
			}
		}
		
		StringBuilder sb = new StringBuilder((String)array[smallerIndex]);
		String next = child.get(array[smallerIndex]).getSmallerWeightPath();
		if (next != "") {
			sb.append("/");
			sb.append(next);
		}
		
		return sb.toString();
	}
	
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return child.toString()+"---";
	}
	
	public int getWeight() {
		return this.weight;
	}

	public void removePath(String[] paths, int index) {
		if (index == paths.length-1 && child.containsKey(paths[index])) {
			child.remove(paths[index]);
		}		
		if (index+1 < paths.length)
			child.get(paths[index]).removePath(paths, index+1);
	}
}
