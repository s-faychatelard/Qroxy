package fr.univmlv.qroxy.cache.tree;

public class TreeCache {
	private final TreeCacheNode root = new TreeCacheNode();

	public void addPath(String path){
		String[] paths = path.split("/");
		root.addChild(paths, 0);
	}
	
	public int getWeight(String path) {
		String[] paths = path.split("/");
		return root.getWeight(paths, 0);
	}
	
	public static void main(String[] args) {
		TreeCache tree = new TreeCache();
		tree.addPath("text/html/http_www.google.fr/index.html");
		tree.addPath("text/html/http_www.google.fr/tmp/1");
		tree.addPath("text/html/http_www.apple.fr/index.html");
		System.out.println(tree.getWeight("text/html/http_www.google.fr"));
		System.out.println(tree.getWeight("text/html/http_www.google.fr/index.html"));
		tree.addPath("text/html/http_www.google.fr/index.html");
		System.out.println(tree.getWeight("text/html/http_www.google.fr/index.html"));
		System.out.println(tree.getWeight("text"));
	}
}
