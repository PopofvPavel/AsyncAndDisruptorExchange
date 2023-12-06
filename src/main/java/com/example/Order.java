package com.example;

public class Order {
    private int clientId;
    private CurrencyPair currencyPair;
    private Type type; // "BUY" или "SELL"
    private double price; //цена

    public int getClientId() {
        return clientId;
    }

    public CurrencyPair getCurrencyPair() {
        return currencyPair;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Order{" +
                "clientId=" + clientId +
                ", currencyPair=" + currencyPair.getBaseCurrency()+"-"+currencyPair.getQuoteCurrency() +
                ", type=" + type +
                ", price=" + price +
                ", volume=" + volume +
                '}';
    }

    public double getPrice() {
        return price;
    }

    public double getVolume() {
        return volume;
    }

    private double volume; //количество
//Купить - значит получить base за quote, продать - получить quote за base
// Цена при покупке - сколько заплатишь quote, чтобы получить 1 base
//Цена при продаже - сколько хочешь за 1 base quote

    public Order(int clientId, CurrencyPair currencyPair, Type type, double price, double volume) {
        this.clientId = clientId;
        this.currencyPair = currencyPair;
        this.type = type;
        this.price = price;
        this.volume = volume;
    }

    public void reduceVolume(double amount) {
        this.volume -= amount;
    }
}
