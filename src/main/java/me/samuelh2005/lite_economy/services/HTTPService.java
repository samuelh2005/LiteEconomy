package me.samuelh2005.lite_economy.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.samuelh2005.lite_economy.LiteEconomy;
import me.samuelh2005.lite_economy.data.AccountOwner;
import me.samuelh2005.lite_economy.data.BankAccount;
import me.samuelh2005.lite_economy.data.Business;
import me.samuelh2005.lite_economy.data.Transaction;
import me.samuelh2005.lite_economy.data.storage.DataStorage;

/**
 * HTTP Service that provides a read-only REST API for LiteEconomy data.
 * Runs a Jetty server on a separate daemon thread.
 */
public class HTTPService {

    private static final int DEFAULT_PORT = 8080;

    private final LiteEconomy main;
    private final Gson gson;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Server server;
    private volatile Thread serverThread;

    public HTTPService(LiteEconomy main) {
        this.main = main;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
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

    private void writeJson(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(gson.toJson(data));
    }

    /**
     * Servlet for /api/accounts endpoint - returns all bank accounts
     */
    private class AccountsServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            List<BankAccount> accounts = List.copyOf(getDataStorage().getBankAccounts().values());
            writeJson(resp, accounts);
        }
    }

    /**
     * Servlet for /api/businesses endpoint - returns all businesses
     */
    private class BusinessesServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            List<Business> businesses = List.copyOf(getDataStorage().getBusinesses().values());
            writeJson(resp, businesses);
        }
    }

    /**
     * Servlet for /api/transactions endpoint - returns all transactions
     */
    private class TransactionsServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            List<Transaction> transactions = List.copyOf(getDataStorage().getTransactions().values());
            writeJson(resp, transactions);
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

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalAccounts", accounts.size());
            stats.put("playerAccounts", playerAccounts);
            stats.put("businessAccounts", businessAccounts);
            stats.put("totalBusinesses", businesses.size());
            stats.put("totalTransactions", transactions.size());
            stats.put("completedTransactions", completedTransactions);
            stats.put("pendingTransactions", pendingTransactions);
            stats.put("totalBalance", totalBalance.toPlainString());
            
            writeJson(resp, stats);
        }
    }
}
