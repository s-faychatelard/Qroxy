package fr.univmlv.qroxy.configuration;

public class ConfigurationType {
	private final int debitmax;
	private final int debitmin;
	private final int weight;
	private final long size;
	
	public ConfigurationType(int debitmax, int debitmin, int weight, long size){
		this.debitmax = debitmax;
		this.debitmin = debitmin;
		this.weight = weight;
		this.size = size;
	}

	public long getSize() {
		return size;
	}
	
	public int getDebitmax() {
		return debitmax;
	}

	public int getDebitmin() {
		return debitmin;
	}

	public int getWeight() {
		return weight;
	}
	@Override
	public String toString() {
		return "max :"+getDebitmax()+"min : "+getDebitmin()+"weight : "+getWeight();
	}
}
