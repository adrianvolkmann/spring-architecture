# ADR-006: DTOs Separados por Operação (Create/Update/Response)

## Status
**Aceito** - 2025-01-04

## Contexto

Em APIs REST, as operações CRUD (Create, Read, Update, Delete, List) frequentemente têm requisitos diferentes quanto aos dados aceitos:

### Problema Identificado

No código atual, usamos o mesmo `ProductRequestDTO` para POST e PUT:

```java
@PostMapping
public ResponseEntity<ProductResponseDTO> create(@Valid @RequestBody ProductRequestDTO dto)

@PutMapping("/{id}")
public ResponseEntity<ProductResponseDTO> update(@PathVariable UUID id, @Valid @RequestBody ProductRequestDTO dto)
```

**Problemas:**
1. **Campos imutáveis**: Alguns campos não devem ser alterados após criação (ex: `sku`, `code`, `createdBy`)
2. **Validações diferentes**: Campo obrigatório no CREATE pode ser opcional no UPDATE
3. **Regras de negócio**: CREATE pode permitir campos que UPDATE proíbe (ou vice-versa)
4. **Segurança**: Campos sensíveis podem ser permitidos apenas na criação
5. **Documentação confusa**: Swagger/OpenAPI não deixa claro quais campos são aceitos em cada operação

### Exemplo Prático

```java
// Cenário atual (problemático)
public record ProductRequestDTO(
    String sku,           // ← Deveria ser imutável (apenas CREATE)
    String name,          // ← Permitido em ambos
    BigDecimal price      // ← Permitido em ambos
) {}

// Cliente pode tentar alterar SKU no UPDATE
PUT /api/products/123
{
  "sku": "NEW-SKU",  // ❌ Não deveria ser permitido
  "name": "Updated Name",
  "price": 99.99
}
```

## Decisão

Adotar **DTOs separados por operação** para todas as entidades do projeto, seguindo o padrão de nomenclatura:

```
<Domain>Request<Operation>DTO
```

### Padrão de Nomenclatura para CRUDL

| Operação | HTTP Method | Endpoint | DTO de Entrada | DTO de Saída |
|----------|-------------|----------|----------------|--------------|
| **Create** | `POST` | `/api/products` | `CreateProductRequestDTO` | `ProductResponseDTO` |
| **Read** (ById) | `GET` | `/api/products/{id}` | — | `ProductResponseDTO` |
| **Update** | `PUT` | `/api/products/{id}` | `UpdateProductRequestDTO` | `ProductResponseDTO` |
| **Delete** | `DELETE` | `/api/products/{id}` | — | — |
| **List** | `GET` | `/api/products` | Query params | `PagedModel<ProductResponseDTO>` |

### Convenção de Nomenclatura

#### Request DTOs (Entrada)

```
Create<Domain>RequestDTO    ← POST (criação)
Update<Domain>RequestDTO    ← PUT (atualização completa)
Patch<Domain>RequestDTO     ← PATCH (atualização parcial) - opcional
<Domain>FilterDTO           ← Query params para listagem - opcional
```

#### Response DTOs (Saída)

```
<Domain>ResponseDTO         ← Resposta padrão para GET/POST/PUT
<Domain>SummaryDTO          ← Resposta simplificada para listagens - opcional
<Domain>DetailDTO           ← Resposta detalhada com relacionamentos - opcional
```

## Justificativa

### Vantagens

#### 1. Separação Clara de Responsabilidades

```java
// CreateProductRequestDTO.java - Campos para criação
public record CreateProductRequestDTO(
    @NotBlank String sku,           // ✅ Obrigatório apenas no CREATE
    @NotBlank String name,
    @NotNull BigDecimal price,
    @NotNull Integer stockQuantity,
    Boolean active                   // Pode ter valor padrão
) {}

// UpdateProductRequestDTO.java - Campos para atualização
public record UpdateProductRequestDTO(
    // sku ausente - não pode ser alterado ✅
    @NotBlank String name,
    @NotNull BigDecimal price,
    @NotNull Integer stockQuantity,
    Boolean active
) {}
```

#### 2. Validações Específicas por Operação

```java
// CREATE - SKU obrigatório, único
public record CreateProductRequestDTO(
    @NotBlank
    @Pattern(regexp = "^[A-Z0-9-]+$")
    @Size(min = 5, max = 20)
    String sku,
    
    @NotBlank String name
) {}

// UPDATE - SKU não existe
public record UpdateProductRequestDTO(
    @NotBlank String name  // Apenas campos editáveis
) {}
```

#### 3. Segurança e Controle de Campos

```java
// CREATE - Permite definir proprietário
public record CreateProductRequestDTO(
    String name,
    UUID ownerId    // ✅ Pode definir na criação
) {}

// UPDATE - Não permite alterar proprietário
public record UpdateProductRequestDTO(
    String name
    // ownerId ausente - não pode ser alterado
) {}
```

#### 4. Documentação Clara (Swagger/OpenAPI)

```yaml
# Swagger gerado automaticamente mostra contratos diferentes

POST /api/products:
  requestBody:
    schema:
      $ref: '#/components/schemas/CreateProductRequestDTO'
      required: [sku, name, price]

PUT /api/products/{id}:
  requestBody:
    schema:
      $ref: '#/components/schemas/UpdateProductRequestDTO'
      required: [name, price]  # sku ausente
```

#### 5. Evolução Independente

- CREATE pode adicionar novos campos sem afetar UPDATE
- UPDATE pode ter regras de validação diferentes sem impactar CREATE
- Cada operação evolui conforme necessidade de negócio

#### 6. Padrão Amplamente Adotado

**Usado por:**
- Google Cloud APIs (ex: `CreateInstanceRequest`, `UpdateInstanceRequest`)
- AWS SDKs (ex: `CreateBucketRequest`, `UpdateBucketRequest`)
- Stripe API (ex: `CustomerCreateParams`, `CustomerUpdateParams`)
- GitHub API (ex: diferentes payloads para POST/PATCH repositories)
- Microsoft Graph API

## Consequências

### Positivas

✅ **Clareza**: Cada DTO reflete exatamente o que a operação aceita  
✅ **Segurança**: Campos imutáveis não podem ser alterados por acidente  
✅ **Validação Precisa**: Regras específicas por operação  
✅ **Documentação**: Swagger/OpenAPI reflete contratos corretos  
✅ **Manutenibilidade**: Mudanças em uma operação não afetam outras  
✅ **Testabilidade**: Testes mais claros por operação  
✅ **SOLID**: Segue Interface Segregation Principle  

### Negativas

⚠️ **Duplicação**: Campos comuns repetidos em múltiplos DTOs  
⚠️ **Mais Arquivos**: 2-3 DTOs por domínio (vs 1 genérico)  
⚠️ **Manutenção**: Mudança em campo comum requer atualização em múltiplos DTOs  

### Mitigações para Duplicação

```java
// Opção 1: Interface comum (Java 17+)
public sealed interface ProductData permits CreateProductRequestDTO, UpdateProductRequestDTO {
    String name();
    BigDecimal price();
}

// Opção 2: Classe base (herança - menos recomendado)
public abstract class BaseProductRequestDTO {
    @NotBlank private String name;
    @NotNull private BigDecimal price;
}

// Opção 3: Composition (records - recomendado)
public record ProductCommonFields(String name, BigDecimal price) {}

public record CreateProductRequestDTO(
    String sku,
    ProductCommonFields common  // Composição
) {}
```

**Decisão:** Aceitar duplicação moderada em favor de clareza. Para projetos com muitos campos comuns, considerar composição.

## Implementação

### Estrutura de Arquivos

```
src/main/java/com/volkmann/demo/
└── dto/
    ├── CreateProductRequestDTO.java    ← POST
    ├── UpdateProductRequestDTO.java    ← PUT
    ├── ProductResponseDTO.java         ← GET/POST/PUT response
    ├── CreateUserRequestDTO.java       ← Outras entidades seguem o mesmo padrão
    ├── UpdateUserRequestDTO.java
    └── UserResponseDTO.java
```

### Exemplo Completo: Product

#### 1. CreateProductRequestDTO (POST)

```java
package com.volkmann.demo.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * DTO for creating a new Product.
 * Used in: POST /api/products
 */
public record CreateProductRequestDTO(
    
    @NotBlank(message = "SKU is required")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "SKU must contain only uppercase letters, numbers, and hyphens")
    @Size(min = 5, max = 20, message = "SKU must be between 5 and 20 characters")
    String sku,
    
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters")
    String name,
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    String description,
    
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
    BigDecimal price,
    
    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity must be greater than or equal to zero")
    Integer stockQuantity,
    
    Boolean active  // Opcional, padrão = true no Service
) {
    // Validações customizadas (se necessário)
    public CreateProductRequestDTO {
        if (active == null) {
            active = true;  // Valor padrão
        }
    }
}
```

#### 2. UpdateProductRequestDTO (PUT)

```java
package com.volkmann.demo.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * DTO for updating an existing Product.
 * Used in: PUT /api/products/{id}
 * 
 * Note: SKU is NOT included as it's immutable after creation.
 */
public record UpdateProductRequestDTO(
    
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters")
    String name,
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    String description,
    
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
    BigDecimal price,
    
    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity must be greater than or equal to zero")
    Integer stockQuantity,
    
    Boolean active
) {
    // Campos ausentes (imutáveis):
    // - sku (definido apenas na criação)
    // - id (vem do path parameter)
    // - createdAt (gerenciado pelo sistema)
    // - updatedAt (gerenciado pelo sistema)
}
```

#### 3. ProductResponseDTO (GET/POST/PUT response)

```java
package com.volkmann.demo.dto;

import com.volkmann.demo.entity.ProductEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for Product responses.
 * Used in: GET/POST/PUT responses
 */
public record ProductResponseDTO(
    UUID id,
    String sku,
    String name,
    String description,
    BigDecimal price,
    Integer stockQuantity,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    /**
     * Converts ProductEntity to ProductResponseDTO.
     */
    public static ProductResponseDTO fromEntity(ProductEntity entity) {
        return new ProductResponseDTO(
            entity.getId(),
            entity.getSku(),
            entity.getName(),
            entity.getDescription(),
            entity.getPrice(),
            entity.getStockQuantity(),
            entity.getActive(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
```

### Controller Atualizado

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    /**
     * CREATE - Create new product
     * POST /api/products
     */
    @PostMapping
    public ResponseEntity<ProductResponseDTO> create(
            @Valid @RequestBody CreateProductRequestDTO dto
    ) {
        ProductResponseDTO product = productService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
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
     * UPDATE - Update existing product
     * PUT /api/products/{id}
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
     * DELETE - Delete product
     * DELETE /api/products/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * LIST - List all products with pagination
     * GET /api/products?page=0&size=20&sort=name,asc
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
}
```

### Service Atualizado

```java
@Service
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Create new product from CreateProductRequestDTO
     */
    @Transactional
    public ProductResponseDTO create(CreateProductRequestDTO dto) {
        // Validar SKU único
        if (productRepository.existsBySku(dto.sku())) {
            throw new IllegalArgumentException("SKU already exists: " + dto.sku());
        }
        
        ProductEntity entity = new ProductEntity();
        entity.setSku(dto.sku());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setPrice(dto.price());
        entity.setStockQuantity(dto.stockQuantity());
        entity.setActive(dto.active());
        
        ProductEntity saved = productRepository.save(entity);
        return ProductResponseDTO.fromEntity(saved);
    }

    /**
     * Update existing product from UpdateProductRequestDTO
     */
    @Transactional
    public ProductResponseDTO update(UUID id, UpdateProductRequestDTO dto) {
        ProductEntity entity = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        
        // SKU não é atualizado (imutável)
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setPrice(dto.price());
        entity.setStockQuantity(dto.stockQuantity());
        entity.setActive(dto.active());
        
        ProductEntity updated = productRepository.save(entity);
        return ProductResponseDTO.fromEntity(updated);
    }
}
```

## Padrões para Operações Especiais

### PATCH (Atualização Parcial)

Se necessário, criar DTO específico para PATCH:

```java
/**
 * DTO for partial updates (PATCH).
 * All fields are Optional - only sent fields are updated.
 */
public record PatchProductRequestDTO(
    @Size(min = 3, max = 255)
    Optional<String> name,
    
    @Size(max = 1000)
    Optional<String> description,
    
    @DecimalMin("0.01")
    Optional<BigDecimal> price,
    
    @Min(0)
    Optional<Integer> stockQuantity,
    
    Optional<Boolean> active
) {}
```

### Bulk Operations

```java
/**
 * DTO for bulk creation.
 */
public record BulkCreateProductRequestDTO(
    @NotEmpty
    @Size(max = 100, message = "Maximum 100 products per batch")
    List<@Valid CreateProductRequestDTO> products
) {}
```

### Filter/Search DTOs (Query Params)

```java
/**
 * DTO for filtering products in list operations.
 * Used as @ModelAttribute in Controller.
 */
public record ProductFilterDTO(
    String name,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Boolean active,
    String sku
) {}

// Controller
@GetMapping
public PagedModel<ProductResponseDTO> findAll(
    @ModelAttribute ProductFilterDTO filter,
    Pageable pageable
) {
    // ...
}
```

## Regras de Validação

### DTOs Request (Create/Update)

- ✅ Usar Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, etc.)
- ✅ Validações específicas por operação
- ✅ Mensagens de erro claras e em inglês (ou i18n)
- ✅ Usar `record` para imutabilidade
- ❌ Não incluir lógica de negócio (apenas validação estrutural)

### DTOs Response

- ✅ Expor apenas dados necessários (não expor campos sensíveis)
- ✅ Incluir timestamps (`createdAt`, `updatedAt`)
- ✅ Incluir ID (UUID)
- ✅ Método estático `fromEntity()` para conversão
- ❌ Não incluir annotations de validação (não são validados)

## Tabela Resumo - Nomenclatura por Operação

| Operação | Method | Endpoint | Request DTO | Response DTO | Campos Imutáveis | Path |
|----------|--------|----------|-------------|--------------|------------------|------|
| **Create** | POST | `/api/products` | `CreateProductRequestDTO` | `ProductResponseDTO` | — | `src/.../dto/Create...DTO.java` |
| **Read** | GET | `/api/products/{id}` | — | `ProductResponseDTO` | — | `src/.../dto/...ResponseDTO.java` |
| **Update** | PUT | `/api/products/{id}` | `UpdateProductRequestDTO` | `ProductResponseDTO` | `id`, `sku`, `createdAt` | `src/.../dto/Update...DTO.java` |
| **Delete** | DELETE | `/api/products/{id}` | — | — | — | — |
| **List** | GET | `/api/products` | Query params | `PagedModel<ProductResponseDTO>` | — | `src/.../dto/...ResponseDTO.java` |
| **Patch** | PATCH | `/api/products/{id}` | `PatchProductRequestDTO` | `ProductResponseDTO` | `id`, `sku`, `createdAt` | `src/.../dto/Patch...DTO.java` |

## Alternativas Consideradas

### Alternativa 1: DTO Único (Atual)

```java
@PostMapping
public ResponseEntity<ProductResponseDTO> create(@Valid @RequestBody ProductRequestDTO dto)

@PutMapping("/{id}")
public ResponseEntity<ProductResponseDTO> update(@PathVariable UUID id, @Valid @RequestBody ProductRequestDTO dto)
```

**Decisão:** ❌ Rejeitado - não permite controle fino de campos por operação.

---

### Alternativa 2: Validation Groups

```java
public record ProductRequestDTO(
    @NotNull(groups = OnCreate.class)
    @Null(groups = OnUpdate.class)
    String sku,
    
    @NotBlank(groups = {OnCreate.class, OnUpdate.class})
    String name
) {}

@PostMapping
public ResponseEntity<ProductResponseDTO> create(@Validated(OnCreate.class) @RequestBody ProductRequestDTO dto)

@PutMapping("/{id}")
public ResponseEntity<ProductResponseDTO> update(@PathVariable UUID id, @Validated(OnUpdate.class) @RequestBody ProductRequestDTO dto)
```

**Decisão:** ❌ Rejeitado - aumenta complexidade, dificulta manutenção, mistura regras.

---

### Alternativa 3: DTOs Separados ✅

```java
@PostMapping
public ResponseEntity<ProductResponseDTO> create(@Valid @RequestBody CreateProductRequestDTO dto)

@PutMapping("/{id}")
public ResponseEntity<ProductResponseDTO> update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequestDTO dto)
```

**Decisão:** ✅ **ACEITO** - clareza, segurança, evolução independente, padrão de mercado.

## Checklist de Code Review

Ao revisar código, verificar:

- [ ] POST usa `Create<Domain>RequestDTO`
- [ ] PUT usa `Update<Domain>RequestDTO`
- [ ] GET retorna `<Domain>ResponseDTO`
- [ ] Campos imutáveis ausentes no `Update<Domain>RequestDTO`
- [ ] Validações apropriadas em cada DTO
- [ ] Service tem métodos separados: `create(CreateDTO)` e `update(UUID, UpdateDTO)`
- [ ] Controller documenta qual DTO usa em cada endpoint
- [ ] Testes cobrem validações específicas de cada DTO

## Exemplos de Outros Domínios

### User (usuário)

```
CreateUserRequestDTO  ← email, password, name
UpdateUserRequestDTO  ← name, avatar (email é imutável)
UserResponseDTO       ← id, email, name, createdAt (sem password)
```

### Order (pedido)

```
CreateOrderRequestDTO  ← customerId, items[], paymentMethod
UpdateOrderRequestDTO  ← status, notes (items não podem ser alterados)
OrderResponseDTO       ← id, customerId, items[], total, status, createdAt
```

### Category (categoria)

```
CreateCategoryRequestDTO  ← name, description, parentId
UpdateCategoryRequestDTO  ← name, description (parentId não pode ser alterado)
CategoryResponseDTO       ← id, name, description, parentId, createdAt
```

## Impacto em Arquivos Existentes

### Arquivos a Criar

```
src/main/java/com/volkmann/demo/dto/
├── CreateProductRequestDTO.java      (NOVO)
└── UpdateProductRequestDTO.java      (NOVO)
```

### Arquivos a Modificar

```
src/main/java/com/volkmann/demo/
├── controller/
│   └── ProductController.java        (trocar ProductRequestDTO por Create/Update)
├── service/
│   └── ProductService.java           (ajustar métodos create/update)
└── dto/
    └── ProductRequestDTO.java        (REMOVER após migração)
```

### Arquivos Sem Mudança

```
src/main/java/com/volkmann/demo/
├── entity/
│   └── ProductEntity.java            (sem mudança)
├── repository/
│   └── ProductRepository.java        (sem mudança)
└── dto/
    └── ProductResponseDTO.java       (sem mudança)
```

## Timeline de Migração

### ✅ Fase 1: Decisão e Documentação
- [x] Criar ADR-006
- [x] Atualizar índice de ADRs

### ⏳ Fase 2: Implementação (Product)
- [ ] Criar `CreateProductRequestDTO`
- [ ] Criar `UpdateProductRequestDTO`
- [ ] Atualizar `ProductController`
- [ ] Atualizar `ProductService`
- [ ] Criar testes para validações específicas
- [ ] Remover `ProductRequestDTO` (deprecated)
- [ ] Executar testes
- [ ] Commit: `refactor: Separate Create/Update DTOs for Product (ADR-006)`

### ⏳ Fase 3: Novos Domínios
- [ ] Aplicar padrão em novas entidades
- [ ] Validar em code review

## Referências

- [Martin Fowler - DTO Pattern](https://martinfowler.com/eaaCatalog/dataTransferObject.html)
- [Google API Design Guide - Standard Methods](https://cloud.google.com/apis/design/standard_methods)
- [Microsoft REST API Guidelines - POST/PUT/PATCH](https://github.com/microsoft/api-guidelines/blob/vNext/Guidelines.md#74-supported-methods)
- [Spring Boot REST Best Practices](https://spring.io/guides/tutorials/rest)
- [Richardson Maturity Model](https://martinfowler.com/articles/richardsonMaturityModel.html)

## ADRs Relacionados

- **ADR-005**: Nomenclatura com sufixos (Entity, Service, Controller, DTO)
- **ADR-001**: UUID como ID (usado em ResponseDTO)
- **ADR-002**: PagedModel para paginação (usado em LIST)

## Revisões

| Data | Autor | Mudança |
|------|-------|---------|
| 2025-01-04 | Arquitetura | Decisão inicial aceita |

## Próximos Passos

1. Implementar `CreateProductRequestDTO` e `UpdateProductRequestDTO`
2. Atualizar Controller e Service
3. Criar testes de validação
4. Aplicar em novos domínios (User, Order, Category, etc.)
5. Considerar ArchUnit para validação automatizada

## Metadados

- **Decisores**: Equipe de Arquitetura
- **Impacto**: Médio (Refatoração de DTOs)
- **Categoria**: API Design, DTOs, CRUD Operations
- **Tags**: #dto #api #crud #nomenclatura #validacao #separacao-responsabilidades
- **Relacionado**: ADR-005 (Nomenclatura), ADR-001 (UUID), ADR-002 (PagedModel)

