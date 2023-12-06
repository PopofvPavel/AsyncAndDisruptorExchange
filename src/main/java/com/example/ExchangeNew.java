package com.example;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExchangeNew implements ExchangeInterface {

    public static final List<CurrencyPair> currencyPairs = new ArrayList<>();
 /*   {
        //Купить - значит получить base за quote, продать - получить quote за base

        currencyPairs.add(new CurrencyPair(Valuta.USD, Valuta.EUR, 0.85));
        currencyPairs.add(new CurrencyPair(Valuta.USD, Valuta.RUB, 90.0));
        currencyPairs.add(new CurrencyPair(Valuta.EUR, Valuta.RUB, 100.0));
        currencyPairs.add(new CurrencyPair(Valuta.BYN, Valuta.RUB, 33.0));

    }*/
    private static class OrderEvent {
        private int clientId;
        private CurrencyPair currencyPair;
        private Type type;
        private double price;
        private double volume;

        private long sequence;

        public long getSequence() {
            return sequence;
        }

        public void setSequence(long sequence) {
            this.sequence = sequence;
        }

        public int getClientId() {
            return clientId;
        }

        public CurrencyPair getCurrencyPair() {
            return currencyPair;
        }

        public Type getType() {
            return type;
        }

        public double getPrice() {
            return price;
        }

        public double getVolume() {
            return volume;
        }

        public void set(int clientId, CurrencyPair currencyPair, Type type, double price, double volume) {
            this.clientId = clientId;
            this.currencyPair = currencyPair;
            this.type = type;
            this.price = price;
            this.volume = volume;
        }
        public void clear() {
            // Очистка данных в объекте OrderEvent
            set(0, null, null, 0.0, 0.0);
        }
    }

    private static class OrderEventTranslator implements EventTranslatorOneArg<OrderEvent, Integer> {
        @Override
        public void translateTo(OrderEvent event, long sequence, Integer clientId) {
            // Вызывается для каждой новой заявки
            // Заполняем поля объекта OrderEvent
            event.set(clientId, null, null, 0.0, 0.0);
            event.setSequence(sequence); // Устанавливаем значение sequence
        }
    }


    private class OrderEventHandler implements EventHandler<OrderEvent> {
        @Override
        public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) throws Exception {
            // Обработка заявки
            if (event.getCurrencyPair() != null) {
                processOrder(event);
            }
        }
    }

    private final int RING_BUFFER_SIZE = 1024;

    private Valuta[] valutas = Valuta.values();
    private final Object monitor = new Object();
    private List<Client> clients = new ArrayList<>();
    //private List<CurrencyPair> currencyPairs = new ArrayList<>();

    private Disruptor<OrderEvent> disruptor = new Disruptor<>(
            OrderEvent::new,
            RING_BUFFER_SIZE,
            DaemonThreadFactory.INSTANCE
    );

    private RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();

    private int processedOrdersCounter = 0;
    private int matchCounter = 0;

    public ExchangeNew() {
        // Инициализация currencyPairs
        currencyPairs.add(new CurrencyPair(Valuta.USD, Valuta.EUR, 0.85));
        currencyPairs.add(new CurrencyPair(Valuta.USD, Valuta.RUB, 90.0));
        currencyPairs.add(new CurrencyPair(Valuta.EUR, Valuta.RUB, 100.0));
        currencyPairs.add(new CurrencyPair(Valuta.BYN, Valuta.RUB, 33.0));

        // Подключаем обработчик событий
        disruptor.handleEventsWith(new OrderEventHandler());
        disruptor.start();

        OrderEventTranslator translator = new OrderEventTranslator();
        disruptor.publishEvent(translator, 0); // Инициализация кольцевого буфера
    }

    @Override
    public void createClient(int clientId) {
        // Создание клиента
        synchronized (monitor) {
            if (isClientIdUnique(clientId)) {
                clients.add(new Client(clientId));
            } else {
                throw new IllegalArgumentException("Клиент с таким ID уже существует.");
            }
        }
    }

    @Override
    public void addBalanceToClient(int clientId, Valuta currency, double amount) throws Exception {
        // Добавление средств на счет клиента
        synchronized (monitor) {
            Client client = getClientById(clientId);
            if (client != null) {
                client.addBalance(currency, amount);
            } else {
                throw new Exception("Клиент с ID " + clientId + " не найден.");
            }
        }
    }

    public  void reduceBalanceToClient(int idClient, Valuta valuta, double moneyReduce) throws Exception {
        Client client = getClientById(idClient);
        if (client != null) {
            client.reduceBalance(valuta, moneyReduce);
        } else {
            throw new Exception("Данный клиент не найден");
        }
    }

    @Override
    public String getClientBalance(int clientId) {
        // Получение баланса клиента
        synchronized (monitor) {
            Client client = getClientById(clientId);
            if (client != null) {
                return client.getFullBalance();
            } else {
                throw new IllegalArgumentException("Клиент с ID " + clientId + " не найден.");
            }
        }
    }

    @Override
    public void createOrder(int clientId, Valuta baseCurrency, Valuta quoteCurrency, Type type, double price, double volume) throws Exception {
        // Создание заявки
        CurrencyPair pair = getCurrencyPair(baseCurrency, quoteCurrency);
        if (pair != null) {
            checkLimits(baseCurrency, quoteCurrency, clientId, type, price, volume);

            // Добавление заявки в кольцевой буфер
            long sequence = ringBuffer.next();
            try {
                OrderEvent orderEvent = ringBuffer.get(sequence);
                orderEvent.set(clientId, pair, type, price, volume);
            } finally {
                ringBuffer.publish(sequence);
            }
        } else {
            throw new Exception("Такая валютная пара не существует.");
        }
    }

    @Override
    public void processOrders() throws Exception {
        // Ничего не делаем, так как обработка происходит асинхронно в обработчике событий
    }

    @Override
    public List<String> getOrdersString() {
        // Не реализовано в этой версии
        return null;
    }

    @Override
    public int getProcessedOrdersCounter() {
        // Получение количества обработанных заявок
        synchronized (monitor) {
            return processedOrdersCounter;
        }
    }

    @Override
    public int getMatchCounter() {
        // Получение количества сделок
        synchronized (monitor) {
            return matchCounter;
        }
    }

    private void processOrder(OrderEvent orderEvent) throws Exception {
        // Обработка заявки
        synchronized (monitor) {
            List<OrderEvent> matchingSellOrders = findMatchingSellOrders(orderEvent);

            for (OrderEvent sellOrder : matchingSellOrders) {
                matchCounter++;
                executeTrade(orderEvent, sellOrder);
            }

            // Удаление обработанной заявки из кольцевого буфера
            orderEvent.clear(); // Очищаем данные в событии
            ringBuffer.publish(orderEvent.getSequence()); // Публикуем последовательность
            processedOrdersCounter++;
        }
    }



    private List<OrderEvent> findMatchingSellOrders(OrderEvent buyOrder) {
        // Поиск заявок на продажу, соответствующих заявке на покупку
        List<OrderEvent> matchingSellOrders = new ArrayList<>();

        for (long sequence = ringBuffer.getMinimumGatingSequence() + 1; sequence <= ringBuffer.getCursor(); sequence++) {
            OrderEvent event = ringBuffer.get(sequence);
            if (event != null && isMatch(buyOrder, event)) {
                matchingSellOrders.add(event);
            }
        }

        return matchingSellOrders;
    }


    private boolean isMatch(OrderEvent buyOrder, OrderEvent sellOrder) {
        // Проверка условий для сопоставления заявок
        return buyOrder.getCurrencyPair().equals(sellOrder.getCurrencyPair()) &&
                buyOrder.getType() == Type.BUY &&
                sellOrder.getType() == Type.SELL &&
                (Math.abs(sellOrder.getPrice() - buyOrder.getPrice()) <= 0.1 * buyOrder.getVolume() * buyOrder.getCurrencyPair().getExchangeRate());
    }

    private void executeTrade(OrderEvent buyOrder, OrderEvent sellOrder) throws Exception {
        // Выполнение сделки
        double amount = Math.min(buyOrder.getVolume(), sellOrder.getVolume());
        double totalPrice = amount * sellOrder.getPrice();

        // Обновление балансов
        Client buyer = getClientById(buyOrder.getClientId());
        buyer.addBalance(buyOrder.getCurrencyPair().getBaseCurrency(), amount);
        buyer.reduceBalance(buyOrder.getCurrencyPair().getQuoteCurrency(), totalPrice);

        Client seller = getClientById(sellOrder.getClientId());
        seller.addBalance(sellOrder.getCurrencyPair().getQuoteCurrency(), totalPrice);
        seller.reduceBalance(sellOrder.getCurrencyPair().getBaseCurrency(), amount);

        // Уменьшение объема заявок после сделки
     /*   buyOrder.setVolume(buyOrder.getVolume() - amount);
        sellOrder.setVolume(sellOrder.getVolume() - amount);*/
    }

    private void checkLimits(Valuta baseCurrency, Valuta quoteCurrency, int clientId, Type type, double price, double volume) throws Exception {
        // Проверка лимитов
        Client client = getClientById(clientId);
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
        // Получение валютной пары по валютам
        for (CurrencyPair currencyPair : currencyPairs) {
            if (currencyPair.getBaseCurrency().equals(baseCurrency) && currencyPair.getQuoteCurrency().equals(quoteCurrency)) {
                return currencyPair;
            }
        }
        return null;
    }

    private boolean isClientIdUnique(int id) {
        // Проверка уникальности ID клиента
        for (Client client : clients) {
            if (client.getId() == id) {
                return false;
            }
        }
        return true;
    }

    private Client getClientById(int id) {
        // Получение клиента по ID
        for (Client client : clients) {
            if (client.getId() == id) {
                return client;
            }
        }
        return null;
    }
}

