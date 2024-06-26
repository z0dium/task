package com.pamihnenkov;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptJava {

    private static ExecutorService executorService = new ThreadPoolExecutor(1,
            1,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());
    private static final Counter counter = new Counter();
    private static final Semaphore semaphore = new Semaphore();
    private final TimeUnit timeUnit;
    private final int requestLimit;


    public CrptJava(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        executorService.execute(new CounterUpdater());
    }

    public void performApiRequest(Document document, String sign){
        boolean done = false;
        while (!done){
            if (semaphore.flag) {
                int currentValue;
                synchronized (counter) {
                    currentValue = counter.getAndShowQtyLeft();

                    if (currentValue < 0) {
                        semaphore.setOff();
                        continue;
                    }
                    else if (currentValue == 0) semaphore.setOff();
                    System.out.println("Left " + currentValue + " calls");
                }
                doRequest(document,sign);
                done = true;
            }
        }
    }

    private void doRequest(Document document,String sign){

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonDocument = objectMapper.writeValueAsString(document);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (JsonProcessingException e) {
            // Как-то отреагировать на исключительные ситуации
        } catch (IOException e) {
            // Как-то отреагировать на исключительные ситуации
        } catch (InterruptedException e) {
            // Как-то отреагировать на исключительные ситуации
        }
    }

    @Getter
    @Setter
    public class Document{
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;  // Тут вероятно была ошибка в задании - непоняное "109" перед полем.
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private Product[] products;
        private String reg_date;
        private String reg_number;
    }

    @Getter
    @Setter
    public class Product{
        private String certificate_document;
        private String certificate_document_date; //Date "2020-01-23"
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date; //Date "2020-01-23"
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    @Getter
    @Setter
    public class Description{
        private String participantInn;
    }

    private class CounterUpdater implements Runnable{
        @Override
        public void run() {
            System.out.println("Started counter");
            while (true){
                counter.updateCounter(requestLimit);
                try{
                    timeUnit.sleep(1);
                }catch (InterruptedException ex){}
                System.out.println("--counter cycle finished");
            }
        }
    }

    private static class Counter {
        private volatile AtomicInteger counter = new AtomicInteger();

        synchronized void updateCounter(int requestLimit){
            counter.set(requestLimit);
            semaphore.setOn();
        }

        synchronized int getAndShowQtyLeft(){
            return counter.decrementAndGet();
        }
    }

    private static class Semaphore{
        private volatile boolean flag;

        Semaphore(){
        }


        void setOff(){
            flag = false;
        }

        void setOn(){
            flag = true;
        }
    }

}
