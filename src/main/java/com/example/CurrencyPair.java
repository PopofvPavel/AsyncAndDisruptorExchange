package com.example;

public class CurrencyPair {
    private final Valuta baseCurrency;
    private final Valuta quoteCurrency;
    private double exchangeRate;

    public Valuta getBaseCurrency() {
        return baseCurrency;
    }

    public Valuta getQuoteCurrency() {
        return quoteCurrency;
    }

    public CurrencyPair(Valuta baseCurrency, Valuta quoteCurrency, double exchangeRate) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.exchangeRate = exchangeRate;
    }


    public double getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(double exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public double ConvertBaseToQuote(double amount) {
        return amount * exchangeRate;
    }

}
