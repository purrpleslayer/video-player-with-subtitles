package com.assignment1;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import com.assignment1.controller.AdminController;

class ShutdownRaceConditionTest {

    // Checks that only one shutdown request is accepted when two requests are racing at the same time.
    @Test
    void onlyOneShutdownRequestIsAccepted() throws Exception {

        // Mock the application context so the test doesn't actually stop Spring.
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

        AdminController controller = new AdminController(context);

        // Keeps both threads waiting until they can start together.
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {

            Future<Integer> request1 = executor.submit(() -> {
                start.await();
                return controller.shutdown().getStatusCode().value();
            });

            Future<Integer> request2 = executor.submit(() -> { 
                start.await();
                return controller.shutdown().getStatusCode().value();
            });

            // Release both shutdown requests at the same time.
            start.countDown();

            // One request should be accepted and the other rejected.
            int status1 = request1.get();
            int status2 = request2.get();

            assertTrue((status1 == 202 && status2 == 409) || (status1 == 409 && status2 == 202));
        }
    }
}

