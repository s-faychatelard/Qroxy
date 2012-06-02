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

	/**
	 * Get the size of cache
	 * 
	 * @return the maximum size
	 */
	public long getSize() {
		return size;
	}
	
	/**
	 * Get the maximum debit
	 * 
	 * @return can be equal to -1
	 */
	public int getDebitmax() {
		return debitmax;
	}

	/**
	 * Get the minimum debit
	 * 
	 * @return can be equal to -1
	 */
	public int getDebitmin() {
		return debitmin;
	}

	/**
	 * Get the weight repartition of a content type
	 * 
	 * @return if not set in the file, is equal to 1
	 */
	public int getWeight() {
		return weight;
	}
	@Override
	public String toString() {
		return "max :"+getDebitmax()+"min : "+getDebitmin()+"weight : "+getWeight();
	}
}
