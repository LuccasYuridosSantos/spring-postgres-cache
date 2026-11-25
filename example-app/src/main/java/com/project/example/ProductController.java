package com.project.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        long start = System.nanoTime();
        ProductService.Lookup lookup = productService.findProduct(id);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return Map.of("product", lookup.product(), "source", lookup.source(), "elapsedMs", elapsedMs);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> evict(@PathVariable String id) {
        return productService.evict(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
