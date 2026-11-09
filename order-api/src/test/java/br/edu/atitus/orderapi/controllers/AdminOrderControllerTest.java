package br.edu.atitus.orderapi.controllers;

import br.edu.atitus.orderapi.services.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AdminOrderControllerTest {

    @Test
    void rejectsNonAdminBeforeCallingOrderService() {
        OrderService orderService = mock(OrderService.class);
        AdminOrderController controller = new AdminOrderController(orderService);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.listOrders(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        1
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(orderService);
    }

    @Test
    void allowsAdminToListOrders() {
        OrderService orderService = mock(OrderService.class);
        AdminOrderController controller = new AdminOrderController(orderService);
        var pageable = PageRequest.of(0, 20);
        when(orderService.findOrdersForAdmin(
                null, null, null, null, null, null, null, pageable
        )).thenReturn(Page.empty(pageable));

        var response = controller.listOrders(
                null, null, null, null, null, null, null, pageable, 0
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(orderService).findOrdersForAdmin(null, null, null, null, null, null, null, pageable);
    }
}
