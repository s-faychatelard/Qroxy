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
	
	public int getWeight(String[] paths, int index) {
		if (index >= paths.length)
			return this.weight;
		String s = paths[index];
		if (child.containsKey(s))
			return child.get(s).getWeight(paths, index+1);
		
		throw new IllegalStateException("Not possible state");
	}
}
