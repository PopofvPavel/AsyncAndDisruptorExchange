package com.example;

import java.util.List;

public interface ExchangeInterface {
    void createClient(int clientId);

    void addBalanceToClient(int clientId, Valuta currency, double amount) throws Exception;

    String getClientBalance(int clientId);

    void createOrder(int clientId, Valuta baseCurrency, Valuta quoteCurrency, Type type, double price, double volume) throws Exception;

    void processOrders() throws Exception;

    List<String> getOrdersString();

    int getProcessedOrdersCounter();

    int getMatchCounter();
}
