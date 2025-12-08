# ADR-005: Sufixo "Entity" em Classes de Domínio JPA

## Status
**Aceito** - 2025-01-04

## Contexto

O projeto possui entidades JPA que representam o modelo de domínio persistido no banco de dados. Atualmente, as classes de entidade usam apenas o nome do domínio sem qualquer sufixo identificador:

```java
@Entity
@Table(name = "products")
public class Product {  // ← Sem sufixo
    // ...
}
```

### Problemas Identificados

1. **Ambiguidade em Buscas**: Ao buscar por "Product" no projeto, retorna:
   - `Product.java` (Entity)
   - `ProductController.java`
   - `ProductService.java`
   - `ProductRepository.java`
   - `ProductRequestDTO.java`
   - `ProductResponseDTO.java`
   
   Não é imediatamente óbvio qual arquivo é a entidade JPA.

2. **Falta de Clareza Visual**: Em imports e referências no código, não fica claro se está usando uma entidade ou DTO:
   ```java
   import com.volkmann.demo.entity.Product;        // Entity ou DTO?
   import com.volkmann.demo.dto.ProductRequestDTO; // Claramente DTO
   ```

3. **Inconsistência com Padrões**: DTOs já seguem sufixos (`RequestDTO`, `ResponseDTO`), mas entidades não.

4. **Navegação no IDE**: Em estruturas de projeto grandes, dificulta identificação rápida de arquivos de entidade.

5. **Conflitos de Nomenclatura**: Em projetos maiores, pode haver necessidade de ter classes de domínio não persistidas com o mesmo nome.

## Decisão

Todas as classes anotadas com `@Entity` devem seguir o padrão de nomenclatura:

```
<Domain>Entity
```

### Exemplos

| Antes | Depois |
|-------|--------|
| `Product` | `ProductEntity` |
| `User` | `UserEntity` |
| `Order` | `OrderEntity` |
| `Category` | `CategoryEntity` |
| `Supplier` | `SupplierEntity` |

### Convenção Completa do Projeto

```
Domain: Product

✅ Camada de Persistência
- ProductEntity.java          (JPA Entity)
- ProductRepository.java      (Spring Data Repository)

✅ Camada de Aplicação
- ProductService.java          (Business Logic)

✅ Camada de Apresentação
- ProductController.java       (REST Controller)
- ProductRequestDTO.java       (Input DTO)
- ProductResponseDTO.java      (Output DTO)

✅ Camada de Exceções
- ProductNotFoundException.java
```

## Justificativa

### Vantagens

#### 1. Identificação Imediata
```java
// Antes - Ambíguo
import com.volkmann.demo.entity.Product;
private Product product;

// Depois - Clara intenção
import com.volkmann.demo.entity.ProductEntity;
private ProductEntity product;
```

#### 2. Busca e Navegação Facilitada

**Busca no IDE por "ProductEntity":**
- ✅ Resultado único e preciso
- ✅ Encontra diretamente a classe de entidade
- ✅ Diferencia de DTOs, Services, Controllers

**Busca por "Product":**
- ⚠️ 6+ resultados
- ⚠️ Necessário filtrar manualmente

#### 3. Separação Conceitual Clara

```java
// Entidade (persistência)
public class ProductEntity {
    @Id private UUID id;
    @Column private String name;
    // Mapeamento JPA, @PrePersist, etc.
}

// DTO (transferência)
public class ProductResponseDTO {
    private UUID id;
    private String name;
    // Apenas dados, sem comportamento JPA
}

// Domain Model (lógica de negócio - futuro)
public class Product {
    private UUID id;
    private String name;
    // Lógica de negócio rica, sem annotations JPA
}
```

#### 4. Preparação para Domain-Driven Design (DDD)

Em arquiteturas DDD, é comum separar:
- **Entidade de Persistência** (`ProductEntity`) - Modelo anêmico com JPA
- **Modelo de Domínio** (`Product`) - Modelo rico com regras de negócio

O sufixo prepara o projeto para essa evolução.

#### 5. Consistência com Nomenclatura Existente

| Camada | Padrão | Exemplo |
|--------|--------|---------|
| Controller | `<Domain>Controller` | `ProductController` |
| Service | `<Domain>Service` | `ProductService` |
| Repository | `<Domain>Repository` | `ProductRepository` |
| DTO Request | `<Domain>RequestDTO` | `ProductRequestDTO` |
| DTO Response | `<Domain>ResponseDTO` | `ProductResponseDTO` |
| **Entity** | **`<Domain>Entity`** | **`ProductEntity`** ✅ |
| Exception | `<Domain>NotFoundException` | `ProductNotFoundException` |

#### 6. Padrão Amplamente Adotado

**Frameworks e projetos que usam sufixo Entity:**
- Jhipster (gerador Spring Boot)
- Muitos projetos enterprise Java
- Documentação de referência Spring Data JPA

**Alternativa (sem sufixo):**
- Spring Boot samples (mais simples)
- Projetos pequenos/protótipos

**Decisão:** Para projetos escaláveis e corporativos, o sufixo é recomendado.

## Consequências

### Positivas

✅ **Clareza**: Identificação imediata de entidades JPA  
✅ **Busca Eficiente**: Resultados precisos em buscas no IDE  
✅ **Manutenibilidade**: Facilita onboarding de novos desenvolvedores  
✅ **Escalabilidade**: Suporta crescimento para DDD/Clean Architecture  
✅ **Consistência**: Alinha com padrão de sufixos (DTO, Controller, Service)  
✅ **Separação de Responsabilidades**: Clara distinção entre camadas  

### Negativas

⚠️ **Verbosidade**: Nomes de classes ligeiramente mais longos  
⚠️ **Refatoração**: Necessário renomear classes existentes  
⚠️ **Imports**: Necessário atualizar todos os imports  
⚠️ **Curva de Aprendizado**: Equipe precisa se adaptar ao novo padrão  

### Neutras

- Annotation `@Table(name = "products")` permanece no plural (nome da tabela)
- Nome da classe não precisa corresponder ao nome da tabela
- Não afeta performance ou comportamento da aplicação

## Implementação

### Passo 1: Renomear Classe

**Antes:**
```java
@Entity
@Table(name = "products")
public class Product {
    // ...
}
```

**Depois:**
```java
@Entity
@Table(name = "products")
public class ProductEntity {
    // ...
}
```

### Passo 2: Atualizar Referências

#### Repository
```java
// Antes
public interface ProductRepository extends JpaRepository<Product, UUID> {
    // ...
}

// Depois
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    // ...
}
```

#### Service
```java
// Antes
private Product toEntity(ProductRequestDTO dto) {
    Product product = new Product();
    // ...
}

// Depois
private ProductEntity toEntity(ProductRequestDTO dto) {
    ProductEntity product = new ProductEntity();
    // ...
}
```

#### DTOs (se houver conversão)
```java
// Antes
public ProductResponseDTO(Product entity) {
    // ...
}

// Depois
public ProductResponseDTO(ProductEntity entity) {
    // ...
}
```

### Passo 3: Atualizar Imports

```java
// Antes
import com.volkmann.demo.entity.Product;

// Depois
import com.volkmann.demo.entity.ProductEntity;
```

## Aplicação da Filosofia às Demais Camadas

Além do sufixo `Entity` para classes JPA, o projeto adota de forma explícita os seguintes sufixos por camada. Os arquivos existentes já seguem este padrão; portanto, **não é necessário modificar código atual** — apenas manter a convenção ao criar novos arquivos.

- `<Domain>Entity` — Entidade JPA (`src/.../entity/`)
- `<Domain>Repository` — Repositório Spring Data JPA (`src/.../repository/`)
- `<Domain>Service` — Serviço / lógica de negócio (`src/.../service/`)
- `<Domain>Controller` — Controller REST (`src/.../controller/`)
- `Create<Domain>RequestDTO` / `Update<Domain>RequestDTO` / `<Domain>ResponseDTO` — DTOs de entrada/saída (`src/.../dto/`) - Ver **ADR-006** para detalhes

Regras operacionais:
- Repository sempre referencia `<Domain>Entity` nos generics.
- Service converte entre DTOs e `<Domain>Entity` (não expõe Entities diretamente na API).
- Controller não referencia `<Domain>Entity`; trabalha apenas com DTOs e Services.
- Não criar arquivos do tipo `migration-entity`; migrações/descrições de alteração devem ser documentadas no chat ou nos ADRs quando necessário.

**Nota sobre DTOs:** A nomenclatura detalhada de DTOs (Create/Update/Response) está documentada em **ADR-006: DTOs Separados por Operação**.

## Padrão de Nomenclatura Completo do Projeto

Esta decisão estabelece o padrão de nomenclatura para **todas as camadas** do projeto, garantindo consistência e facilitando navegação.

### Convenção Geral: `<Domain><Sufixo>`

Todas as classes devem seguir o padrão `<Domain><Sufixo>`, onde:
- **`<Domain>`**: Nome do domínio em PascalCase (ex: `Product`, `User`, `Order`)
- **`<Sufixo>`**: Identificador da camada/responsabilidade

### Tabela de Sufixos por Camada

| Camada | Sufixo | Exemplo | Responsabilidade |
|--------|--------|---------|------------------|
| **Entidade JPA** | `Entity` | `ProductEntity` | Mapeamento ORM, persistência |
| **Repository** | `Repository` | `ProductRepository` | Acesso a dados (Spring Data JPA) |
| **Service** | `Service` | `ProductService` | Lógica de negócio, transações |
| **Controller** | `Controller` | `ProductController` | Endpoints REST, validação HTTP |
| **DTO Request** | `RequestDTO` | `ProductRequestDTO` | Dados de entrada da API |
| **DTO Response** | `ResponseDTO` | `ProductResponseDTO` | Dados de saída da API |
| **Exception** | `Exception` | `ProductNotFoundException` | Exceções específicas do domínio |

### Estrutura Completa por Domínio

Para cada domínio (ex: `Product`), a estrutura de arquivos deve ser:

```
src/main/java/com/volkmann/demo/
├── entity/
│   └── ProductEntity.java              ← @Entity (ORM/Persistência)
├── repository/
│   └── ProductRepository.java          ← Interface Spring Data JPA
├── service/
│   └── ProductService.java             ← @Service (Lógica de negócio)
├── controller/
│   └── ProductController.java          ← @RestController (API REST)
├── dto/
│   ├── ProductRequestDTO.java          ← record (Entrada da API)
│   └── ProductResponseDTO.java         ← record (Saída da API)
└── exception/
    └── ProductNotFoundException.java   ← extends RuntimeException
```

## Exemplos Práticos por Camada

### 1. Entity - Sufixo `Entity` ✅

```java
package com.volkmann.demo.entity;

@Entity
@Table(name = "products")
public class ProductEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    // Getters, setters, etc.
}
```

**Justificativa:**
- Identifica imediatamente como entidade JPA
- Diferencia de DTOs e Domain Models
- Prepara para evolução DDD (separar Entity de Domain Model)

---

### 2. Repository - Sufixo `Repository` ✅

```java
package com.volkmann.demo.repository;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    
    Page<ProductEntity> findByActiveTrue(Pageable pageable);
    
    Page<ProductEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
    
    Optional<ProductEntity> findByCode(String code);
}
```

**Justificativa:**
- Padrão Spring Data (convenção do framework)
- Identifica como camada de acesso a dados
- Sempre trabalha com `<Domain>Entity` nos generics

**Regra:** Repository SEMPRE referencia `<Domain>Entity`, nunca DTOs.

---

### 3. Service - Sufixo `Service` ✅

```java
package com.volkmann.demo.service;

@Service
public class ProductService {
    
    private final ProductRepository productRepository;
    
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }
    
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findAll(String name, boolean onlyActive, Pageable pageable) {
        Page<ProductEntity> products = productRepository.findAll(pageable);
        return products.map(ProductResponseDTO::fromEntity);
    }
    
    @Transactional
    public ProductResponseDTO create(ProductRequestDTO dto) {
        ProductEntity entity = new ProductEntity();
        // Mapear DTO → Entity
        entity.setName(dto.name());
        // ...
        
        ProductEntity saved = productRepository.save(entity);
        return ProductResponseDTO.fromEntity(saved);
    }
}
```

**Justificativa:**
- Padrão Spring (convenção do framework)
- Identifica como camada de lógica de negócio
- Recebe `<Domain>RequestDTO`, trabalha com `<Domain>Entity`, retorna `<Domain>ResponseDTO`

**Regra:** Service converte entre DTOs e Entities, nunca expõe Entities diretamente.

---

### 4. Controller - Sufixo `Controller` ✅

```java
package com.volkmann.demo.controller;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    private final ProductService productService;
    
    public ProductController(ProductService productService) {
        this.productService = productService;
    }
    
    @GetMapping
    public PagedModel<ProductResponseDTO> findAll(
        @RequestParam(required = false) String name,
        @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        Page<ProductResponseDTO> products = productService.findAll(name, false, pageable);
        return new PagedModel<>(products);
    }
    
    @PostMapping
    public ResponseEntity<ProductResponseDTO> create(@Valid @RequestBody ProductRequestDTO dto) {
        ProductResponseDTO created = productService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

**Justificativa:**
- Padrão Spring MVC (convenção do framework)
- Identifica como camada de apresentação/API
- Trabalha APENAS com DTOs, nunca com Entities

**Regra:** Controller NUNCA referencia `<Domain>Entity`, apenas DTOs e Services.

---

### 5. DTO Request - Sufixo `RequestDTO` ✅

```java
package com.volkmann.demo.dto;

public record ProductRequestDTO(
    @NotBlank String name,
    @NotNull BigDecimal price,
    String description,
    Integer stockQuantity,
    Boolean active
) {
    // Validações via Bean Validation
}
```

**Justificativa:**
- Sufixo `RequestDTO` indica dados de ENTRADA da API
- Separação clara entre input e output
- Validações centralizadas com Bean Validation

**Regra:** Usar `record` para imutabilidade e concisão.

---

### 6. DTO Response - Sufixo `ResponseDTO` ✅

```java
package com.volkmann.demo.dto;

public record ProductResponseDTO(
    UUID id,
    String name,
    String description,
    BigDecimal price,
    Integer stockQuantity,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ProductResponseDTO fromEntity(ProductEntity entity) {
        return new ProductResponseDTO(
            entity.getId(),
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

**Justificativa:**
- Sufixo `ResponseDTO` indica dados de SAÍDA da API
- Método `fromEntity` centraliza conversão Entity → DTO
- Controla exatamente quais campos são expostos na API

**Regra:** Sempre ter método estático `fromEntity(DomainEntity entity)` para conversão.

---

### 7. Exception - Sufixo `Exception` ✅

```java
package com.volkmann.demo.exception;

public class ProductNotFoundException extends ResourceNotFoundException {
    
    public ProductNotFoundException(String message) {
        super(message);
    }
    
    public ProductNotFoundException(UUID id) {
        super("Product not found with id: " + id);
    }
}
```

**Justificativa:**
- Padrão Java (convenção da linguagem)
- Identifica como exceção de domínio
- Permite tratamento específico por tipo

---

## Regras de Interação entre Camadas

### Fluxo de Dados Correto ✅

```
HTTP Request (JSON)
    ↓
Controller recebe ProductRequestDTO
    ↓
Controller chama ProductService
    ↓
Service converte ProductRequestDTO → ProductEntity
    ↓
Service chama ProductRepository
    ↓
Repository persiste ProductEntity no banco
    ↓
Repository retorna ProductEntity
    ↓
Service converte ProductEntity → ProductResponseDTO
    ↓
Service retorna ProductResponseDTO ao Controller
    ↓
Controller retorna ProductResponseDTO
    ↓
HTTP Response (JSON)
```

### Regras de Dependência

| Camada | Pode Referenciar | NÃO Pode Referenciar |
|--------|-----------------|----------------------|
| **Controller** | Service, DTOs | ❌ Entity, Repository |
| **Service** | Repository, Entity, DTOs | ✅ Tudo exceto Controller |
| **Repository** | Entity | ❌ Service, Controller, DTOs |
| **Entity** | Nada (modelo puro) | ❌ Service, Repository, Controller, DTOs |
| **DTOs** | Entity (apenas no método `fromEntity`) | ❌ Service, Repository, Controller |

### Validação de Código

```java
// ✅ CORRETO - Service trabalha com Entity
@Service
public class ProductService {
    public ProductResponseDTO findById(UUID id) {
        ProductEntity entity = repository.findById(id)...
        return ProductResponseDTO.fromEntity(entity);
    }
}

// ❌ ERRADO - Controller NÃO deve referenciar Entity
@RestController
public class ProductController {
    public ResponseEntity<ProductEntity> findById(UUID id) {  // ❌ NUNCA!
        return service.findById(id);
    }
}

// ✅ CORRETO - Controller trabalha apenas com DTOs
@RestController
public class ProductController {
    public ResponseEntity<ProductResponseDTO> findById(UUID id) {  // ✅
        return ResponseEntity.ok(service.findById(id));
    }
}
```

## Padrão de Migração

### Para Entidades Existentes
1. Renomear arquivo: `Product.java` → `ProductEntity.java`
2. Renomear classe interna
3. Usar refactor do IDE (Alt+Shift+R no IntelliJ) para atualizar referências
4. Verificar imports em todos os arquivos
5. Executar testes para validar

### Para Novas Classes (Qualquer Camada)

**Template obrigatório:**
```
<Domain><Sufixo>.java

Exemplos:
- UserEntity.java
- UserRepository.java
- UserService.java
- UserController.java
- UserRequestDTO.java
- UserResponseDTO.java
```

### Para Novas Entidades
```java
// Template obrigatório
@Entity
@Table(name = "<table_name>")
public class <Domain>Entity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;
    
    // ... campos e métodos
}
```

## Validação

### Checklist de Code Review

- [ ] Classe está no pacote `entity`
- [ ] Classe possui annotation `@Entity`
- [ ] Nome da classe termina com `Entity`
- [ ] Repository referencia a classe correta
- [ ] Service referencia a classe correta
- [ ] Testes compilam e passam
- [ ] Nenhum import do nome antigo permanece

### Ferramenta de Validação (ArchUnit - Futuro)

```java
@ArchTest
static final ArchRule entities_must_have_entity_suffix =
    classes()
        .that().areAnnotatedWith(Entity.class)
        .should().haveSimpleNameEndingWith("Entity")
        .because("ADR-005: Entities must have 'Entity' suffix for clarity");
```

## Exemplos de Aplicação

### Entidade Simples
```java
@Entity
@Table(name = "categories")
public class CategoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String name;
}
```

### Entidade com Relacionamento
```java
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;  // ← Também com sufixo
    
    @OneToMany(mappedBy = "order")
    private List<OrderItemEntity> items;  // ← Também com sufixo
}
```

### Repository
```java
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    List<OrderEntity> findByCustomer(CustomerEntity customer);
}
```

## Alternativas Consideradas

### Alternativa 1: Sem Sufixo (Padrão Atual)
```java
public class Product { }
```
- **Prós**: Mais conciso, menos verboso
- **Contras**: Ambíguo, dificulta busca, não escala para DDD
- **Decisão**: ❌ Rejeitado - não resolve problemas de clareza

### Alternativa 2: Prefixo "Jpa"
```java
public class JpaProduct { }
```
- **Prós**: Também identifica como persistência
- **Contras**: Menos comum, pode confundir com classes do framework
- **Decisão**: ❌ Rejeitado - prefixo é menos idiomático

### Alternativa 3: Pacote "model" separado
```
entity/       ← Apenas entities
model/        ← Domain models (sem JPA)
```
- **Prós**: Separação clara por pacote
- **Contras**: Não resolve ambiguidade dentro de cada pacote
- **Decisão**: ❌ Rejeitado - sufixo é mais explícito

### Alternativa 4: Sufixo "Entity" ✅
```java
public class ProductEntity { }
```
- **Prós**: Clareza máxima, padrão amplamente adotado, suporta DDD
- **Contras**: Ligeiramente mais verboso
- **Decisão**: ✅ **ACEITO**

## Impacto em Arquivos

### Arquivos a Modificar (Migração Atual)
```
src/main/java/com/volkmann/demo/
├── entity/
│   └── Product.java                    → ProductEntity.java
├── repository/
│   └── ProductRepository.java          (atualizar generics)
├── service/
│   └── ProductService.java             (atualizar referências)
├── dto/
│   ├── ProductRequestDTO.java          (sem mudança)
│   └── ProductResponseDTO.java         (sem mudança)
└── controller/
    └── ProductController.java          (sem mudança)
```

### Arquivos Futuros
Toda nova entidade deve seguir: `<Domain>Entity.java`

## Timeline de Migração

### ✅ Fase 1: Decisão e Documentação (Hoje)
- [x] Criar ADR-005
- [x] Atualizar índice de ADRs

### ✅ Fase 2: Migração de Product (Hoje)
- [ ] Renomear `Product` → `ProductEntity`
- [ ] Atualizar `ProductRepository`
- [ ] Atualizar `ProductService`
- [ ] Executar testes
- [ ] Commit com mensagem: `refactor: Rename Product to ProductEntity (ADR-005)`

### ⏳ Fase 3: Novas Entidades (Futuro)
- [ ] Aplicar padrão em novas entidades
- [ ] Validar em code review

### ⏳ Fase 4: Validação Automatizada (Opcional)
- [ ] Implementar ArchUnit test
- [ ] Adicionar ao CI/CD pipeline

## Comunicação

### Mensagem para Equipe

```
📢 Novo Padrão: Sufixo "Entity" em Classes JPA

A partir de agora, todas as classes @Entity devem ter sufixo "Entity":

✅ Correto:   ProductEntity, UserEntity, OrderEntity
❌ Incorreto: Product, User, Order

Motivo: Facilitar identificação e busca de entidades no projeto.

Referência: DOCUMENTACAO/ADR-005-sufixo-entity.md

Dúvidas? Consulte o ADR ou fale com a equipe de arquitetura.
```

## Referências

- [Domain-Driven Design - Eric Evans](https://www.domainlanguage.com/ddd/)
- [JHipster Naming Conventions](https://www.jhipster.tech/)
- [Spring Data JPA - Best Practices](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Clean Architecture - Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

## ADRs Relacionados

- **ADR-001**: UUID como Identificador Padrão (usado em todas as entities)

## Revisões

| Data | Autor | Mudança |
|------|-------|---------|
| 2025-01-04 | Arquitetura | Decisão inicial aceita e aplicada em ProductEntity |

## Próximos Passos

1. ✅ Aplicar em `Product` → `ProductEntity`
2. Criar template para novas entidades
3. Adicionar ao guia de desenvolvimento
4. Considerar ArchUnit para validação automatizada

## Metadados

- **Decisores**: Equipe de Arquitetura
- **Impacto**: Médio (Refatoração de Nomenclatura)
- **Categoria**: Code Standards, Naming Conventions, Maintainability
- **Tags**: #entity #nomenclatura #jpa #padrao-codigo #ddd-ready
- **Relacionado**: ADR-001 (UUID)
