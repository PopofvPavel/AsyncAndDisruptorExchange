package com.example;

import java.util.HashMap;
import java.util.Map;

public class Client {
    private final int id;
    private Map<Valuta, Double> balance;


    public Client(int id) {
        this.id = id;
        this.balance = new HashMap<>();
    }


    public int getId() {
        return id;
    }

    public String getFullBalance() {
        String result = "Баланс клиента  " + getId() + "\n";
        for (Valuta val : balance.keySet()) {
            result += val.name() + " : " + balance.get(val) + "\n";
        }

        return result;
    }

    public double getBalanceOfValuta(Valuta valuta) {
        return this.balance.getOrDefault(valuta,0.0);
    }

    public void addBalance(Valuta valuta, double money) {
        double current;
        current = balance.getOrDefault(valuta, 0.0);
        balance.put(valuta, current + money);
    }

    public void reduceBalance(Valuta valuta, double moneyReduce) throws Exception {
        double current;
        if (!balance.containsKey(valuta)) {
            throw new Exception("У данного клииента нет такой валюты");
        }
        if (balance.get(valuta) < moneyReduce) {
            throw new Exception("У данного клиента недостаточно денег для списания");
        }
        current = balance.get(valuta);
        balance.put(valuta, current - moneyReduce);
    }
}
