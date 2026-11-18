package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.dtos.ProductImageResponse;
import br.edu.atitus.productapi.dtos.ProductWithImages;
import br.edu.atitus.productapi.entities.CategoryEntity;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.entities.SubcategoryEntity;
import br.edu.atitus.productapi.enums.ProductType;
import br.edu.atitus.productapi.services.ProductService;
import br.edu.atitus.productapi.servicesimpl.AuthorizationService;
import br.edu.atitus.productapi.servicesimpl.ProductImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WsProductControllerTest {

    private static final String PRODUCT_BODY = "{\"name\":\"Produto\",\"brand\":\"Marca\",\"model\":\"Modelo\","
            + "\"description\":\"Descrição\",\"currency\":\"BRL\",\"price\":10,\"subcategoryId\":2}";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private ProductService service;
    private ProductImageService imageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ProductService.class);
        imageService = mock(ProductImageService.class);
        mockMvc = standaloneSetup(new WsProductController(
                service, new AuthorizationService(), imageService, objectMapper)).build();
    }

    @Test
    void productUpdateRequiresAnAdministrator() throws Exception {
        mockMvc.perform(put("/ws/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_BODY)
                        .header("X-User-Type", 2))
                .andExpect(status().isForbidden());

        verify(service, never()).update(eq(1L), any());
    }

    @Test
    void productStatusUpdateRequiresAnAdministrator() throws Exception {
        mockMvc.perform(patch("/ws/products/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}")
                        .header("X-User-Type", 2))
                .andExpect(status().isForbidden());

        verify(service, never()).updateStatus(eq(1L), any());
    }

    @Test
    void administratorCanUpdateProductAndStatus() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(fullProduct());
        when(service.updateStatus(eq(1L), any())).thenReturn(fullProduct());
        when(imageService.findProductWithImages(1L))
                .thenReturn(new ProductWithImages(fullProduct(), null, List.of()));

        mockMvc.perform(put("/ws/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_BODY)
                        .header("X-User-Type", 0))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/ws/products/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}")
                        .header("X-User-Type", 0))
                .andExpect(status().isOk());

        verify(service).update(eq(1L), any());
        verify(service).updateStatus(eq(1L), any());
    }

    @Test
    void adminResponsesKeepEveryOldKeyAndOnlyAddImageFields() throws Exception {
        // Formato antigo: a entity serializada diretamente (subcategory e category aninhados).
        JsonNode before = objectMapper.valueToTree(fullProduct());
        when(service.save(any())).thenReturn(fullProduct());
        when(service.update(eq(1L), any())).thenReturn(fullProduct());
        when(service.updateStatus(eq(1L), any())).thenReturn(fullProduct());
        var images = List.of(new ProductImageResponse(9L, "https://cdn/products/1/a.png", true, 1));
        when(imageService.findProductWithImages(1L))
                .thenReturn(new ProductWithImages(fullProduct(), "https://cdn/products/1/a.png", images));

        List<JsonNode> responses = List.of(
                read(mockMvc.perform(post("/ws/products")
                                .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_BODY)
                                .header("X-User-Id", 1).header("X-User-Email", "admin@test").header("X-User-Type", 0))
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()),
                read(mockMvc.perform(put("/ws/products/1")
                                .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_BODY)
                                .header("X-User-Type", 0))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()),
                read(mockMvc.perform(patch("/ws/products/1/status")
                                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")
                                .header("X-User-Type", 0))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
        );

        Set<String> oldKeys = keyPaths(before);
        assertTrue(oldKeys.containsAll(Set.of("id", "name", "image", "productType", "active",
                "subcategory.id", "subcategory.name", "subcategory.category.name")), "fixture incompleta: " + oldKeys);
        for (JsonNode after : responses) {
            Set<String> newKeys = keyPaths(after);
            assertTrue(newKeys.containsAll(oldKeys), "chaves ausentes: " + difference(oldKeys, newKeys));
            Set<String> added = difference(newKeys, oldKeys);
            assertTrue(added.stream().allMatch(key -> key.equals("images") || key.startsWith("images[].")),
                    "chaves inesperadas: " + added);
            for (String key : oldKeys) {
                assertEquals(nodeTypeAt(before, key), nodeTypeAt(after, key), "tipo alterado em " + key);
            }
            assertEquals("https://cdn/products/1/a.png", after.get("image").asString());
            assertEquals(9, after.get("images").get(0).get("id").asInt());
            assertTrue(after.get("images").get(0).get("isPrimary").asBoolean());
            assertEquals("Porongo", after.get("subcategory").get("name").asString());
            assertEquals("Cuias", after.get("subcategory").get("category").get("name").asString());
        }
    }

    private JsonNode read(String json) {
        return objectMapper.readTree(json);
    }

    private static Set<String> keyPaths(JsonNode node) {
        Set<String> keys = new LinkedHashSet<>();
        collect(node, "", keys);
        return keys;
    }

    private static void collect(JsonNode node, String prefix, Set<String> keys) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String key = prefix + entry.getKey();
                keys.add(key);
                collect(entry.getValue(), key + ".", keys);
            }
        } else if (node.isArray() && !node.isEmpty()) {
            collect(node.get(0), prefix.substring(0, prefix.length() - 1) + "[].", keys);
        }
    }

    private static String nodeTypeAt(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            current = part.endsWith("[]")
                    ? current.get(part.substring(0, part.length() - 2)).get(0)
                    : current.get(part);
        }
        return current.getNodeType().name();
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> result = new LinkedHashSet<>(a);
        result.removeAll(b);
        return result;
    }

    private static ProductEntity fullProduct() {
        var category = new CategoryEntity();
        category.setId(2L);
        category.setName("Cuias");
        category.setSlug("cuias");
        category.setActive(true);
        var subcategory = new SubcategoryEntity();
        subcategory.setId(7L);
        subcategory.setName("Porongo");
        subcategory.setSlug("porongo");
        subcategory.setActive(true);
        subcategory.setCategory(category);
        var product = new ProductEntity();
        product.setId(1L);
        product.setName("Cuia artesanal");
        product.setBrand("Marca");
        product.setModel("CU-01");
        product.setDescription("Cuia de porongo");
        product.setCurrency("BRL");
        product.setPrice(new BigDecimal("75.00"));
        product.setImage("https://example.com/legado.jpg");
        product.setProductType(ProductType.SINGLE_UNIT);
        product.setActive(true);
        product.setSubcategory(subcategory);
        return product;
    }
}
