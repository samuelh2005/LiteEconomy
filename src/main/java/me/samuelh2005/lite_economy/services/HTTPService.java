package me.samuelh2005.lite_economy.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.samuelh2005.lite_economy.LiteEconomy;
import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Business.BusinessMember;
import me.samuelh2005.lite_economy.data.Transaction;
import me.samuelh2005.lite_economy.data.storage.DataStorage;

/**
 * HTTP Service that provides a read-only REST API for LiteEconomy data.
 * Runs a Jetty server on a separate daemon thread.
 */
public class HTTPService {
    
    private static final int DEFAULT_PORT = 8080;
    
    private final LiteEconomy main;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Server server;
    private volatile Thread serverThread;
    
    public HTTPService(LiteEconomy main) {
        this.main = main;
    }
    
    /**
     * Starts the HTTP server on the specified port.
     * @param port The port to listen on
     */
    public void start(int port) {
        if (running.getAndSet(true)) {
            LiteEconomy.LOGGER.warn("HTTP Service is already running");
            return;
        }
        
        serverThread = Thread.ofPlatform()
            .name("LiteEconomy-HTTPServer")
            .daemon(true)
            .start(() -> runServer(port));
    }
    
    /**
     * Starts the HTTP server on the default port (8080).
     */
    public void start() {
        start(DEFAULT_PORT);
    }
    
    private void runServer(int port) {
        server = new Server(port);
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
        
        // Register servlets for each endpoint
        context.addServlet(new ServletHolder(new AccountsServlet()), "/api/accounts/*");
        context.addServlet(new ServletHolder(new BusinessesServlet()), "/api/businesses/*");
        context.addServlet(new ServletHolder(new TransactionsServlet()), "/api/transactions/*");
        context.addServlet(new ServletHolder(new StatsServlet()), "/api/stats");
        
        try {
            server.start();
            LiteEconomy.LOGGER.info("HTTP Service started on port {}", port);
            server.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LiteEconomy.LOGGER.info("HTTP Service interrupted");
        } catch (Exception e) {
            LiteEconomy.LOGGER.error("Failed to start HTTP Service", e);
        } finally {
            running.set(false);
        }
    }
    
    /**
     * Stops the HTTP server.
     */
    public void stop() {
        running.set(false);
        Server srv = server;
        if (srv != null) {
            try {
                srv.stop();
                srv.join();
            } catch (Exception e) {
                LiteEconomy.LOGGER.error("Error stopping HTTP Service", e);
            }
        }
        
        Thread thread = serverThread;
        if (thread != null) {
            thread.interrupt();
        }
    }
    
    /**
     * Checks if the HTTP server is running.
     */
    public boolean isRunning() {
        return running.get();
    }
    
    private DataStorage getDataStorage() {
        return main.getDataStorage();
    }
    
    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }
    
    private void writeJson(HttpServletResponse response, JsonObject json) throws IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(json.toString());
    }
    
    private void writeJson(HttpServletResponse response, JsonArray json) throws IOException {
        setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(json.toString());
    }
    
    private JsonObject serializeBankAccount(BankAccount account) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", account.getId().toString());
        obj.addProperty("accountName", account.getAccountName());
        obj.addProperty("balance", account.getBalance().toPlainString());
        obj.add("owner", serializeAccountOwner(account.getOwner()));
        return obj;
    }
    
    private JsonObject serializeAccountOwner(AccountOwner owner) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", owner.getType().name().toLowerCase());
        obj.addProperty("id", owner.getId().toString());
        obj.addProperty("name", owner.getName());
        return obj;
    }
    
    private JsonObject serializeBusiness(Business business) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", business.getId().toString());
        obj.addProperty("name", business.getName());
        
        JsonArray membersArray = new JsonArray();
        for (BusinessMember member : business.getMembers()) {
            JsonObject memberObj = new JsonObject();
            memberObj.addProperty("playerId", member.getPlayerId().toString());
            memberObj.addProperty("role", member.getRole().name().toLowerCase());
            membersArray.add(memberObj);
        }
        obj.add("members", membersArray);
        
        return obj;
    }
    
    private JsonObject serializeTransaction(Transaction transaction) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", transaction.getId().toString());
        obj.addProperty("actor", transaction.getActorId().toString());
        
        if (transaction.getFromId().isPresent()) {
            obj.addProperty("from", transaction.getFromId().get().toString());
        }
        if (transaction.getToId().isPresent()) {
            obj.addProperty("to", transaction.getToId().get().toString());
        }
        
        obj.addProperty("amount", transaction.getAmount().toPlainString());
        obj.addProperty("createdAtEpochMs", transaction.getCreatedAtEpochMs());
        
        if (transaction.getCompletedAtEpochMs().isPresent()) {
            obj.addProperty("completedAtEpochMs", transaction.getCompletedAtEpochMs().get());
        }
        
        obj.addProperty("status", transaction.getStatus().name().toLowerCase());
        
        if (transaction.getLocation().isPresent()) {
            obj.addProperty("location", transaction.getLocation().get());
        }
        
        return obj;
    }
    
    /**
     * Servlet for /api/accounts endpoint - returns all bank accounts
     */
    private class AccountsServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            List<BankAccount> accounts = List.copyOf(getDataStorage().getBankAccounts().values());
            JsonArray jsonArray = new JsonArray();
            for (BankAccount account : accounts) {
                jsonArray.add(serializeBankAccount(account));
            }
            writeJson(resp, jsonArray);
        }
        
        @Override
        protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            setCorsHeaders(resp);
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }
    
    /**
     * Servlet for /api/businesses endpoint - returns all businesses
     */
    private class BusinessesServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            List<Business> businesses = List.copyOf(getDataStorage().getBusinesses().values());
            JsonArray jsonArray = new JsonArray();
            for (Business business : businesses) {
                jsonArray.add(serializeBusiness(business));
            }
            writeJson(resp, jsonArray);
        }
        
        @Override
        protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            setCorsHeaders(resp);
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }
    
    /**
     * Servlet for /api/transactions endpoint - returns all transactions
     */
    private class TransactionsServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            List<Transaction> transactions = List.copyOf(getDataStorage().getTransactions().values());
            JsonArray jsonArray = new JsonArray();
            for (Transaction transaction : transactions) {
                jsonArray.add(serializeTransaction(transaction));
            }
            writeJson(resp, jsonArray);
        }
        
        @Override
        protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            setCorsHeaders(resp);
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }
    
    /**
     * Servlet for /api/stats endpoint - returns economic statistics
     */
    private class StatsServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            DataStorage storage = getDataStorage();

            Map<UUID, BankAccount> accounts = storage.getBankAccounts();
            Map<UUID, Business> businesses = storage.getBusinesses();
            Map<UUID, Transaction> transactions = storage.getTransactions();

            BigDecimal totalBalance = accounts.values().stream()
                .map(BankAccount::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            long playerAccounts = accounts.values().stream()
                .filter(a -> a.getOwner().getType() == AccountOwner.Type.PLAYER)
                .count();

            long businessAccounts = accounts.values().stream()
                .filter(a -> a.getOwner().getType() == AccountOwner.Type.BUSINESS)
                .count();

            long completedTransactions = transactions.values().stream()
                .filter(t -> t.getStatus() == Transaction.Status.COMPLETED_SUCCESS)
                .count();

            long pendingTransactions = transactions.values().stream()
                .filter(t -> t.getStatus() == Transaction.Status.PENDING)
                .count();

            JsonObject stats = new JsonObject();
            stats.addProperty("totalAccounts", accounts.size());
            stats.addProperty("playerAccounts", playerAccounts);
            stats.addProperty("businessAccounts", businessAccounts);
            stats.addProperty("totalBusinesses", businesses.size());
            stats.addProperty("totalTransactions", transactions.size());
            stats.addProperty("completedTransactions", completedTransactions);
            stats.addProperty("pendingTransactions", pendingTransactions);
            stats.addProperty("totalBalance", totalBalance.toPlainString());
            
            writeJson(resp, stats);
        }
        
        @Override
        protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            setCorsHeaders(resp);
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }
}
