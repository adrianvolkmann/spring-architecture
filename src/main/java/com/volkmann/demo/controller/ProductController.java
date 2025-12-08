package com.volkmann.demo.controller;

import com.volkmann.demo.dto.CreateProductRequestDTO;
import com.volkmann.demo.dto.UpdateProductRequestDTO;
import com.volkmann.demo.dto.ProductResponseDTO;
import com.volkmann.demo.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * LIST - List all products with pagination
     * GET /api/products?page=0&size=20&sort=name,asc
     *
     * Returns PagedModel as per ADR-002 (PagedModel as Pagination Standard)
     * Pagination limits configured in ADR-003:
     * - default-page-size: 20
     * - max-page-size: 100
     */
    @GetMapping
    public PagedModel<ProductResponseDTO> findAll(
            @RequestParam(required = false) String name,
            @RequestParam(required = false, defaultValue = "false") boolean onlyActive,
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        Page<ProductResponseDTO> products = productService.findAll(name, onlyActive, pageable);
        return new PagedModel<>(products);
    }

    /**
     * READ - Find product by ID
     * GET /api/products/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> findById(@PathVariable UUID id) {
        ProductResponseDTO product = productService.findById(id);
        return ResponseEntity.ok(product);
    }

    /**
     * CREATE - Create new product
     * POST /api/products
     *
     * As per ADR-006: Uses CreateProductRequestDTO for creation
     */
    @PostMapping
    public ResponseEntity<ProductResponseDTO> create(@Valid @RequestBody CreateProductRequestDTO dto) {
        ProductResponseDTO product = productService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    /**
     * UPDATE - Update existing product
     * PUT /api/products/{id}
     *
     * As per ADR-006: Uses UpdateProductRequestDTO for updates
     * Immutable fields (id, createdAt, updatedAt) are not included in DTO
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequestDTO dto
    ) {
        ProductResponseDTO product = productService.update(id, dto);
        return ResponseEntity.ok(product);
    }

    /**
     * DELETE - Delete product (hard delete)
     * DELETE /api/products/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivate product (soft delete)
     * PATCH /api/products/{id}/deactivate
     */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}

