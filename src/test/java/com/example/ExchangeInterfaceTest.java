package com.example;


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeInterfaceTest {
    @Test
    void testAllRealizations() throws InterruptedException {
        ExchangeNew exchangeNew = new ExchangeNew();
        Exchange exchange = new Exchange();
        int oldCounter = testCreateOrder(exchange);
        int newCounter = testCreateOrder(exchangeNew);
        assertTrue(oldCounter > 0);
        assertTrue(newCounter > 0);
        System.out.println("Old exchange = " + oldCounter);
        System.out.println("New exchange = " + newCounter);

    }




    int testCreateOrder(ExchangeInterface exchange) throws InterruptedException {
        //ExchangeInterface exchange = new ExchangeNew(); // Или ExchangeNew, в зависимости от того, что вы хотите тестировать
        //ExchangeInterface exchange = new Exchange(); // Или ExchangeNew, в зависимости от того, что вы хотите тестировать
        int numThreads = 5; // Количество потоков
        int numOrdersPerThread = 1000; // Количество заявок на каждом потоке
        CountDownLatch latch = new CountDownLatch(numThreads);

        int numClients = 1000;
        for (int i = 1; i <= numClients; i++) {
            exchange.createClient(i);
            try {
                exchange.addBalanceToClient(i, Valuta.USD, 100000.0);
                exchange.addBalanceToClient(i, Valuta.RUB, 100000.0);
                exchange.addBalanceToClient(i, Valuta.EUR, 100000.0);
                exchange.addBalanceToClient(i, Valuta.BYN, 100000.0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Создаем потоки для генерации заявок
        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < numOrdersPerThread; j++) {
                        // Генерируем случайные заявки
                        int clientId = (int) (Math.random() * 20) + 1;
                        // Создаем список возможных комбинаций валют

                        List<CurrencyPair> possiblePairs = new ArrayList<>();
                        possiblePairs.add(new CurrencyPair(Valuta.USD, Valuta.EUR, 0.85));
                        possiblePairs.add(new CurrencyPair(Valuta.USD, Valuta.RUB, 90.0));
                        possiblePairs.add(new CurrencyPair(Valuta.EUR, Valuta.RUB, 100.0));
                        possiblePairs.add(new CurrencyPair(Valuta.BYN, Valuta.RUB, 33.0));

                        if (possiblePairs.isEmpty()) {
                            // Нет доступных валютных пар
                            return;
                        }
                        Random random = new Random();
                        // Выбираем случайную валютную пару из списка
                        CurrencyPair currencyPair = possiblePairs.get(random.nextInt(possiblePairs.size()));

                        Valuta baseCurrency = currencyPair.getBaseCurrency();
                        Valuta quoteCurrency = currencyPair.getQuoteCurrency();
                        Type type = Math.random() < 0.5 ? Type.BUY : Type.SELL;
                        double price = Math.random() * 100;
                        double volume = Math.random() * 10;

                  /*      double priceStep = 0.1;
                        double price = currencyPair.getExchangeRate() + random.nextDouble() * priceStep;
                        double volume = (random.nextDouble()+ 1) * 10;*/

                        // Создаем заявку
                        try {
                            exchange.createOrder(clientId, baseCurrency, quoteCurrency, type, price, volume);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Ожидаем завершения всех потоков
        latch.await();

        // Ждем некоторое время для обработки заявок
        Thread.sleep(1000);

        // Проверяем, что все заявки были обработаны
        assertTrue(exchange.getProcessedOrdersCounter() > 0);
        System.out.println("Заявок прошло: " + exchange.getProcessedOrdersCounter());
        return exchange.getProcessedOrdersCounter();
    }
}