package com.example;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;


public class Exchange implements ExchangeInterface {
    private final Valuta[] valutas = Valuta.values();
    private final Object monitor = new Object();
    private final List<Client> clients = new ArrayList<>();
    public static final List<CurrencyPair> currencyPairs = new ArrayList<>();

    private final BlockingQueue<Order> orderQueue = new ArrayBlockingQueue<>(10000);
    private final ExecutorService orderProcessor = Executors.newSingleThreadExecutor();




    private int processedOrdersCounter = 0;
    private int matchCounter = 0;

    {
        //Купить - значит получить base за quote, продать - получить quote за base

        currencyPairs.add(new CurrencyPair(Valuta.USD, Valuta.EUR, 0.85));
        currencyPairs.add(new CurrencyPair(Valuta.USD, Valuta.RUB, 90.0));
        currencyPairs.add(new CurrencyPair(Valuta.EUR, Valuta.RUB, 100.0));
        currencyPairs.add(new CurrencyPair(Valuta.BYN, Valuta.RUB, 33.0));

    }


    public List<String> getOrdersString() {
        String result = "Список orders:\n";
        for (Order order : orderQueue) {
            result += order.toString();
        }
        return Collections.singletonList(result);
    }

    public Order getOrderFromList(int idClient) {
        for (Order order : orderQueue) {
            if (order.getClientId() == idClient) {
                return order;
            }
        }
        return null;
    }

    public void createClient(int id) {
        // Проверить, есть ли уже клиент с таким id
        if (isClientIdUnique(id)) {
            Client client = new Client(id);
            clients.add(client);
        } else {
            throw new IllegalArgumentException("Такой id же существует");
        }
    }

    private boolean isClientIdUnique(int id) {
        for (Client client : clients) {
            if (client.getId() == id) {
                return false; // Клиент с таким id уже существует
            }
        }
        return true; // Клиент с таким id не существует
    }

    public String getClientBalance(int id) {
        for (Client client : clients) {
            if (client.getId() == id) {
                return client.getFullBalance(); // Клиент с таким id уже существует
            }
        }
        return "Данный клиент не найден: " + id;

    }

    public double getClientBalanceInValute(int id, Valuta valuta) {
        for (Client client : clients) {
            if (client.getId() == id) {
                return client.getBalanceOfValuta(valuta); // Клиент с таким id уже существует
            }
        }
        return 0.0;
    }

    public Client getClientFromList(int id) {
        for (Client client : clients) {
            if (client.getId() == id) {
                return client;
            }
        }
        return null;
    }

    public void addBalanceToClient(int idClient, Valuta valuta, double money) throws Exception {
        Client client = getClientFromList(idClient);
        if (client != null) {
            client.addBalance(valuta, money);
        } else {
            throw new Exception("Данный клиент не найден");
        }

    }

    public void reduceBalanceToClient(int idClient, Valuta valuta, double moneyReduce) throws Exception {
        Client client = getClientFromList(idClient);
        if (client != null) {
            client.reduceBalance(valuta, moneyReduce);
        } else {
            throw new Exception("Данный клиент не найден");
        }
    }


    public void createOrder(int clientId, Valuta baseCurrency, Valuta quoteCurrency, Type type, double price, double volume) throws Exception {
        CurrencyPair pair = getCurrencyPair(baseCurrency, quoteCurrency);
        if (pair != null) {
            checkLimits(baseCurrency, quoteCurrency, clientId, type, price, volume);
            Order order = new Order(clientId, pair, type, price, volume);
            orderQueue.offer(order);  // Помещаем заявку в очередь
            processOrdersAsync();  // Начинаем обработку заявок сразу
        } else {
            throw new Exception("Такая валютная пара не существует.");
        }
    }

    private void checkLimits(Valuta baseCurrency, Valuta quoteCurrency, int clientId, Type type, double price, double volume) throws Exception {
        Client client = getClientFromList(clientId);
        if (volume == 0.0) {
            throw new Exception("Нельзя создать заявку на 0 покупки или продажи");
        }
        if (client == null) {
            throw new Exception("Данный клиент не найден");
        }
        if (type == Type.BUY) {
            if (client.getBalanceOfValuta(quoteCurrency) < price * volume) {
                throw new Exception("У клиента недостаточно денег для покупки:\n"
                        + "Чтобы купить " + volume + " " + baseCurrency.name() + " необходимо " + price * volume
                        + " " + quoteCurrency.name() + "\n" + "Доступно: " + client.getBalanceOfValuta(quoteCurrency) + quoteCurrency.name()
                );
            }
        } else if (type == Type.SELL) {
            if (client.getBalanceOfValuta(baseCurrency) < volume) {
                throw new Exception("У клиента недостаточно денег для продажи:\n"
                        + "Чтобы продать " + volume + " " + baseCurrency.name()
                        + "\n" + "Доступно: " + client.getBalanceOfValuta(baseCurrency) + baseCurrency.name()
                );
            }
        }

        double exchangeRate = getCurrencyPair(baseCurrency, quoteCurrency).getExchangeRate();
        System.out.println("rate = " + exchangeRate);
        double upperLimit = 1.1 * exchangeRate;
        System.out.println("upperLimit" + upperLimit);
        double lowerLimit = 0.9 * exchangeRate;
        if (price > upperLimit) {
            throw new Exception("Слишком высокая цена, выход за лимит: " + upperLimit);
        }
        if (price < lowerLimit) {
            throw new Exception("Слишком низкая цена, выход за лимит" + lowerLimit);
        }


    }

    private CurrencyPair getCurrencyPair(Valuta baseCurrency, Valuta quoteCurrency) {
        for (CurrencyPair currencyPair : currencyPairs) {
            if (currencyPair.getBaseCurrency().equals(baseCurrency) && currencyPair.getQuoteCurrency().equals(quoteCurrency)) {
                return currencyPair;
            }
        }
        return null;
    }

    private void processOrdersAsync() throws InterruptedException {
        orderProcessor.submit(() -> {
            while (true) {
                try {
                    Order order = orderQueue.poll(500, TimeUnit.MILLISECONDS); // ждем 0.5 секунд, чтобы избежать блокировки на пустой очереди
                    if (order != null) {
                        processOrder(order);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    e.printStackTrace();  // Обработка ошибок
                } finally {
                    //processingLatch.countDown(); // Сигнализируем о завершении обработки заявок
                }
            }
        });


    }


    public void processOrders() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                processOrdersAsync();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public void waitForProcessing() throws InterruptedException {
        //processingLatch.await();
        orderProcessor.awaitTermination(10000, TimeUnit.MILLISECONDS);
        orderProcessor.shutdown();//shutdownnow
    }

    private void processOrder(Order order) throws Exception {
        List<Order> matchingSellOrders = findMatchingSellOrders(order);

        for (Order sellOrder : matchingSellOrders) {
            matchCounter++;
            executeTrade(order, sellOrder);
            if (sellOrder.getVolume() <= 0) {
                // Удаление обработанной заявки из списка
                orderQueue.remove(sellOrder);
            }
        }

        if (order.getVolume() <= 0) {
            // Удаление обработанной заявки из списка
            orderQueue.remove(order);
        }

        System.out.println("Конец обработки заявки для клиента " + order.getClientId() + " NEW");
    }

    public void shutdown() {
        orderProcessor.shutdownNow();
    }

    private List<Order> findMatchingSellOrders(Order buyOrder) {
        List<Order> matchingSellOrders = new ArrayList<>();

        for (Order sellOrder : orderQueue) {
            if (isMatch(buyOrder, sellOrder)) {
                matchingSellOrders.add(sellOrder);
            }
        }

        return matchingSellOrders;
    }


    public boolean isMatch(Order buyOrder, Order sellOrder) {
        // Проверка условий для сопоставления заявок
        return buyOrder.getCurrencyPair().equals(sellOrder.getCurrencyPair()) &&  // Проверка валютной пары
                buyOrder.getType() == Type.BUY && sellOrder.getType() == Type.SELL &&  // Проверка типов заявок
                (Math.abs(sellOrder.getPrice() - buyOrder.getPrice()) <= 0.1 * buyOrder.getVolume() * buyOrder.getCurrencyPair().getExchangeRate());
        // buyOrder.getPrice() >= sellOrder.getPrice();  // Проверка цены
    }


    private void executeTrade(Order buyOrder, Order sellOrder) throws Exception {
        // Выполнение сделки и обновление балансов клиентов
        double amount = Math.min(buyOrder.getVolume(), sellOrder.getVolume());
        double totalPrice = amount * sellOrder.getPrice();

        if (amount > 0) {  // Проверка на ненулевой объем перед выполнением сделки
            // Обновление балансов
            Client buyer = getClientFromList(buyOrder.getClientId());
            buyer.addBalance(buyOrder.getCurrencyPair().getBaseCurrency(), amount);
            buyer.reduceBalance(buyOrder.getCurrencyPair().getQuoteCurrency(), totalPrice);

            Client seller = getClientFromList(sellOrder.getClientId());
            seller.addBalance(sellOrder.getCurrencyPair().getQuoteCurrency(), totalPrice);
            seller.reduceBalance(sellOrder.getCurrencyPair().getBaseCurrency(), amount);
            System.out.println("Провели заявку на " + amount + buyOrder.getCurrencyPair().getBaseCurrency().name() + " NEW");
            processedOrdersCounter++;
            // Уменьшение объема заявок после сделки
            buyOrder.reduceVolume(amount);
            sellOrder.reduceVolume(amount);
        }
        // Обновление балансов
     /*   Client buyer = getClientFromList(buyOrder.getClientId());
        buyer.addBalance(buyOrder.getCurrencyPair().getBaseCurrency(), amount);
        buyer.reduceBalance(buyOrder.getCurrencyPair().getQuoteCurrency(), totalPrice);

        Client seller = getClientFromList(sellOrder.getClientId());
        seller.addBalance(sellOrder.getCurrencyPair().getQuoteCurrency(), totalPrice);
        seller.reduceBalance(sellOrder.getCurrencyPair().getBaseCurrency(), amount);

        // Уменьшение объема заявок после сделки
        System.out.println("Провели заявку на " + amount + buyOrder.getCurrencyPair().getBaseCurrency().name());
        processedOrdersCounter++;
        buyOrder.reduceVolume(amount);
        sellOrder.reduceVolume(amount);*/

    }

    public int getProcessedOrdersCounter() {
        return processedOrdersCounter;
    }

    public int getMatchCounter() {
        return matchCounter;
    }
}
