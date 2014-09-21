package org.tinyejb.test.ejbs.stateful;

public class CartItem {
	private int qty;
	private String item;
	private double unitPrice;
	private String category;

	public CartItem(String category, int qty, String item, double unitPrice) {
		this.category = category;
		this.qty = qty;
		this.item = item;
		this.unitPrice = unitPrice;
	}

	public int getQty() {
		return qty;
	}

	public String getItem() {
		return item;
	}

	public double getUnitPrice() {
		return unitPrice;
	}

	public String toString() {
		StringBuffer b = new StringBuffer();

		b.append("Category: ").append(category).append(", Item: ").append(item).append(", qty: ").append(qty).append(", price: ").append(unitPrice).append(", sub-total: ").append(qty * unitPrice);

		return b.toString();
	}

	public String getCategory() {
		return category;
	}
}
