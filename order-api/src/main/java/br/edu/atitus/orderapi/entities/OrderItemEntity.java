package br.edu.atitus.orderapi.entities;

import br.edu.atitus.orderapi.dtos.ProductResponse;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;

@Entity
@Table(name = "tb_order_item")
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID do produto vindo do product-service
    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal priceAtPurchase;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currencyAtPurchase; // guarda a moeda no momento da compra

    @Column(precision = 19, scale = 2)
    private BigDecimal catalogPriceAtPurchase;

    @Column(length = 3)
    private String catalogCurrencyAtPurchase;

    @Column(columnDefinition = "TEXT")
    private String productName;

    @Column(columnDefinition = "TEXT")
    private String productBrand;

    @Column(columnDefinition = "TEXT")
    private String productModel;

    @Column(columnDefinition = "TEXT")
    private String productDescription;

    @Column(columnDefinition = "TEXT")
    private String productImage;

    private Long categoryId;

    @Column(columnDefinition = "TEXT")
    private String categoryName;

    private Long subcategoryId;

    @Column(columnDefinition = "TEXT")
    private String subcategoryName;

    @Column(length = 32)
    private String productType;

    private Boolean productActive;

    @Column(columnDefinition = "TEXT")
    private String productEnvironment;

    @Column(columnDefinition = "TEXT")
    private String promotionMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private OrderEntity order;

    @Transient
    private ProductResponse product;

    @Transient
    private BigDecimal convertedPriceAtPruchase = BigDecimal.ZERO;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPriceAtPurchase() {
        return priceAtPurchase;
    }

    public void setPriceAtPurchase(BigDecimal priceAtPurchase) {
        this.priceAtPurchase = priceAtPurchase;
    }

    public BigDecimal getUnitPrice() {
        return priceAtPurchase;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.priceAtPurchase = unitPrice;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public String getCurrencyAtPurchase() {
        return currencyAtPurchase;
    }

    public void setCurrencyAtPurchase(String currencyAtPurchase) {
        this.currencyAtPurchase = currencyAtPurchase;
    }

    public BigDecimal getCatalogPriceAtPurchase() {
        return catalogPriceAtPurchase;
    }

    public void setCatalogPriceAtPurchase(BigDecimal catalogPriceAtPurchase) {
        this.catalogPriceAtPurchase = catalogPriceAtPurchase;
    }

    public String getCatalogCurrencyAtPurchase() {
        return catalogCurrencyAtPurchase;
    }

    public void setCatalogCurrencyAtPurchase(String catalogCurrencyAtPurchase) {
        this.catalogCurrencyAtPurchase = catalogCurrencyAtPurchase;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductBrand() {
        return productBrand;
    }

    public void setProductBrand(String productBrand) {
        this.productBrand = productBrand;
    }

    public String getProductModel() {
        return productModel;
    }

    public void setProductModel(String productModel) {
        this.productModel = productModel;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public String getProductImage() {
        return productImage;
    }

    public void setProductImage(String productImage) {
        this.productImage = productImage;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getSubcategoryId() {
        return subcategoryId;
    }

    public void setSubcategoryId(Long subcategoryId) {
        this.subcategoryId = subcategoryId;
    }

    public String getSubcategoryName() {
        return subcategoryName;
    }

    public void setSubcategoryName(String subcategoryName) {
        this.subcategoryName = subcategoryName;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public Boolean getProductActive() {
        return productActive;
    }

    public void setProductActive(Boolean productActive) {
        this.productActive = productActive;
    }

    public String getProductEnvironment() {
        return productEnvironment;
    }

    public void setProductEnvironment(String productEnvironment) {
        this.productEnvironment = productEnvironment;
    }

    public String getPromotionMessage() {
        return promotionMessage;
    }

    public void setPromotionMessage(String promotionMessage) {
        this.promotionMessage = promotionMessage;
    }

    public OrderEntity getOrder() {
        return order;
    }

    public void setOrder(OrderEntity order) {
        this.order = order;
    }

    public ProductResponse getProduct() {
        return product;
    }

    public void setProduct(ProductResponse product) {
        this.product = product;
    }

    public BigDecimal getConvertedPriceAtPruchase() {
        return convertedPriceAtPruchase;
    }

    public void setConvertedPriceAtPruchase(BigDecimal convertedPriceAtPruchase) {
        this.convertedPriceAtPruchase = convertedPriceAtPruchase;
    }


}