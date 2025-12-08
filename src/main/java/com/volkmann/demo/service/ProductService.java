package com.volkmann.demo.service;

import com.volkmann.demo.dto.CreateProductRequestDTO;
import com.volkmann.demo.dto.UpdateProductRequestDTO;
import com.volkmann.demo.dto.ProductResponseDTO;
import com.volkmann.demo.entity.ProductEntity;
import com.volkmann.demo.exception.ResourceNotFoundException;
import com.volkmann.demo.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findAll(String name, boolean onlyActive, Pageable pageable) {
        Page<ProductEntity> products;

        if (name != null && !name.isBlank()) {
            products = productRepository.findByNameContainingIgnoreCase(name, pageable);
        } else if (onlyActive) {
            products = productRepository.findByActiveTrue(pageable);
        } else {
            products = productRepository.findAll(pageable);
        }

        return products.map(ProductResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO findById(UUID id) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return ProductResponseDTO.fromEntity(product);
    }

    @Transactional
    public ProductResponseDTO create(CreateProductRequestDTO dto) {

        ProductEntity product = new ProductEntity();
        product.setName(dto.name());
        product.setDescription(dto.description());
        product.setPrice(dto.price());
        product.setStockQuantity(dto.stockQuantity());
        product.setActive(dto.active() != null ? dto.active() : true);

        ProductEntity savedProduct = productRepository.save(product);
        return ProductResponseDTO.fromEntity(savedProduct);
    }

    @Transactional
    public ProductResponseDTO update(UUID id, UpdateProductRequestDTO dto) {

        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        product.setName(dto.name());
        product.setDescription(dto.description());
        product.setPrice(dto.price());
        product.setStockQuantity(dto.stockQuantity());
        if (dto.active() != null) {
            product.setActive(dto.active());
        }

        ProductEntity updatedProduct = productRepository.save(product);
        return ProductResponseDTO.fromEntity(updatedProduct);
    }

    @Transactional
    public void delete(UUID id) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        productRepository.delete(product);
    }

    @Transactional
    public void deactivate(UUID id) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        product.setActive(false);
        productRepository.save(product);
    }
}

