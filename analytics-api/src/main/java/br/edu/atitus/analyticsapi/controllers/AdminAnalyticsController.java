package br.edu.atitus.analyticsapi.controllers;

import br.edu.atitus.analyticsapi.dtos.AnalyticsFilter;
import br.edu.atitus.analyticsapi.dtos.CollectionsResponse;
import br.edu.atitus.analyticsapi.dtos.InventoryResponses;
import br.edu.atitus.analyticsapi.dtos.SalesResponses;
import br.edu.atitus.analyticsapi.enums.Granularity;
import br.edu.atitus.analyticsapi.enums.ProductRanking;
import br.edu.atitus.analyticsapi.services.AnalyticsAuthorizationService;
import br.edu.atitus.analyticsapi.services.AnalyticsQueryService;
import br.edu.atitus.analyticsapi.services.CollectionService;
import br.edu.atitus.analyticsapi.services.InventoryQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Endpoints administrativos do dashboard. Todos exigem X-User-Type = 0 (admin),
 * injetado pelo Gateway a partir do JWT.
 */
@RestController
@RequestMapping("/ws/admin/analytics")
public class AdminAnalyticsController {

    private static final int MAX_LIMIT = 100;

    private final AnalyticsQueryService queryService;
    private final InventoryQueryService inventoryQueryService;
    private final CollectionService collectionService;
    private final AnalyticsAuthorizationService authorizationService;

    public AdminAnalyticsController(
            AnalyticsQueryService queryService,
            InventoryQueryService inventoryQueryService,
            CollectionService collectionService,
            AnalyticsAuthorizationService authorizationService
    ) {
        this.queryService = queryService;
        this.inventoryQueryService = inventoryQueryService;
        this.collectionService = collectionService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/overview")
    public ResponseEntity<SalesResponses.Overview> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long productId,
            @RequestHeader(name = "X-User-Type", required = false) Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.ok(queryService.overview(
                AnalyticsFilter.of(from, to, state, region, categoryId, productId)));
    }

    @GetMapping("/sales")
    public ResponseEntity<SalesResponses.SalesSeries> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false, defaultValue = "DAY") String granularity,
            @RequestHeader(name = "X-User-Type", required = false) Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.ok(queryService.salesSeries(
                AnalyticsFilter.of(from, to, state, region, categoryId, productId),
                parseGranularity(granularity)));
    }

    @GetMapping("/sales/by-region")
    public ResponseEntity<SalesResponses.RegionReport> salesByRegion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long productId,
            @RequestHeader(name = "X-User-Type", required = false) Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.ok(queryService.byRegion(
                AnalyticsFilter.of(from, to, state, region, categoryId, productId)));
    }

    @GetMapping("/products/top")
    public ResponseEntity<SalesResponses.TopProducts> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false, defaultValue = "UNITS") String rankBy,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestHeader(name = "X-User-Type", required = false) Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.ok(queryService.topProductsReport(
                AnalyticsFilter.of(from, to, state, region, categoryId, productId),
                parseRanking(rankBy),
                clampLimit(limit)));
    }

    @GetMapping("/categories")
    public ResponseEntity<SalesResponses.CategoryReport> categories(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestHeader(name = "X-User-Type", required = false) Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.ok(queryService.categories(
                AnalyticsFilter.of(from, to, state, region, categoryId, productId),
                clampLimit(limit)));
    }

    @GetMapping("/price-ranges")
    public ResponseEntity<SalesResponses.PriceRangeReport> priceRanges(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long productId,
            @RequestHeader(name = "X-User-Type", required = false) Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.ok(queryService.priceRanges(
                AnalyticsFilter.of(from, to, state, region, categoryId, productId)));
    }

    @GetMapping("/inventory")
    public ResponseEntity<InventoryResponses.Report> inventory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "DAY") String granularity,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestHeader(name = "X-User-Type", required = false) Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        AnalyticsFilter period = AnalyticsFilter.of(from, to, null, null, null, null);
        return ResponseEntity.ok(inventoryQueryService.report(
                period.from(), period.to(), parseGranularity(granularity), clampLimit(limit)));
    }

    @GetMapping("/inventory/movements")
    public ResponseEntity<InventoryResponses.MovementPage> inventoryMovements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String movementType,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestHeader(name = "X-User-Type", required = false) Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        if (page < 0) {
            throw AnalyticsFilter.badRequest("O parâmetro 'page' não pode ser negativo.");
        }
        AnalyticsFilter period = AnalyticsFilter.of(from, to, null, null, null, null);
        return ResponseEntity.ok(inventoryQueryService.movements(
                period.from(), period.to(), productId, movementType, page, clampLimit(size)));
    }

    /** Controle de execução: situação de cada fonte e histórico das últimas 20 execuções. */
    @GetMapping("/collections")
    public ResponseEntity<CollectionsResponse> collectionStatus(
            @RequestHeader(name = "X-User-Type", required = false) Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        return ResponseEntity.ok(collectionService.status());
    }

    /** Dispara a coleta manualmente (útil após indisponibilidade das origens). */
    @PostMapping("/collections/run")
    public ResponseEntity<CollectionsResponse> runCollection(
            @RequestHeader(name = "X-User-Type", required = false) Integer userType
    ) {
        authorizationService.requireAdmin(userType);
        boolean executed = collectionService.runAll();
        return ResponseEntity.status(executed ? HttpStatus.OK : HttpStatus.ACCEPTED).body(collectionService.status());
    }

    private static Granularity parseGranularity(String value) {
        try {
            return Granularity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw AnalyticsFilter.badRequest("Granularidade inválida: " + value + ". Use DAY, WEEK ou MONTH.");
        }
    }

    private static ProductRanking parseRanking(String value) {
        try {
            return ProductRanking.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw AnalyticsFilter.badRequest("Ordenação inválida: " + value + ". Use UNITS ou REVENUE.");
        }
    }

    private static int clampLimit(int limit) {
        if (limit < 1) {
            throw AnalyticsFilter.badRequest("O limite deve ser maior que zero.");
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
