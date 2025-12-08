# ADR-009: Nomenclatura de Chave Primária - Usar Apenas "id"

## Status
**Aceito** - 2025-01-04

## Contexto

Em entidades JPA, a chave primária (primary key) precisa de um nome de atributo. Existem duas convenções principais no mercado:

### Opção 1: Genérico (`id`)
```java
@Entity
public class ProductEntity {
    @Id
    private UUID id;
}
```

### Opção 2: Prefixado com Domínio (`productId`)
```java
@Entity
public class ProductEntity {
    @Id
    private UUID productId;
}
```

### Problemas Identificados

#### 1. **Inconsistência entre Projetos**
Sem um padrão definido, cada desenvolvedor escolhe uma convenção diferente:
```java
// Dev 1:
private UUID id;

// Dev 2:
private UUID productId;

// Dev 3:
private UUID product_id;
```

#### 2. **Verbosidade Desnecessária**
```java
// Com prefixo:
UUID productId = product.getProductId(); // Redundante
product.setProductId(uuid);

// Sem prefixo:
UUID id = product.getId(); // Conciso
product.setId(uuid);
```

#### 3. **Configurações Extras no Spring Data**
```java
// Com "productId", Spring Data não funciona automaticamente:
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    @Query("SELECT p FROM ProductEntity p WHERE p.productId = :id")
    Optional<ProductEntity> findById(@Param("id") UUID id);
}

// Com "id", funciona sem configuração:
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    // findById() funciona automaticamente ✅
}
```

#### 4. **Ambiguidade em Joins SQL (Argumento para Prefixo)**
```sql
-- Com "id" (ambíguo):
SELECT p.id, o.id, c.id
FROM products p
JOIN orders o ON p.id = o.product_id
JOIN customers c ON o.customer_id = c.id;
-- ❌ Qual "id"?

-- Com "product_id" (claro):
SELECT p.product_id, o.order_id, c.customer_id
FROM products p
JOIN orders o ON p.product_id = o.product_id
JOIN customers c ON o.customer_id = c.customer_id;
-- ✅ Claro
```

**Mas:** Pode ser resolvido com aliases sem mudar código Java.

---

## Decisão

Adotar **`id`** como nome padrão para chaves primárias em todas as entidades JPA.

### Padrão Obrigatório

```java
@Entity
@Table(name = "<table_name>")
public class <Domain>Entity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id; // ✅ SEMPRE "id"
    
    // ... outros campos
}
```

### Convenção de Nomenclatura

| Camada | Nome do Campo | Exemplo |
|--------|---------------|---------|
| **Java (Atributo)** | `id` | `private UUID id;` |
| **Java (Getter/Setter)** | `getId()`, `setId()` | `product.getId()` |
| **Banco de Dados (Coluna)** | `id` (preferencial) ou mapeado | `@Column(name = "product_id")` se necessário |
| **JSON/API** | `id` | `{ "id": "123e4567..." }` |

---

## Justificativa

### Vantagens de Usar `id`

#### 1. **Convenção Oficial Spring Data JPA**

**Spring Data JPA Documentation:**
> "By default, Spring Data JPA assumes the primary key is named `id`."

```java
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    // ✅ findById(), existsById(), deleteById() funcionam automaticamente
}
```

**Com `productId`:**
```java
// ⚠️ Precisa sobrescrever métodos ou configurar @EntityGraph
```

---

#### 2. **Padrão JPA/Jakarta Persistence**

**JPA Specification:**
- Não obriga nome específico, mas **convenção é `id`**
- Hibernate otimiza para `id` (assume como padrão)

```java
@Entity
public class Product {
    @Id // ← Convenção assume "id"
    private Long id;
}
```

---

#### 3. **Menos Verbosidade**

```java
// ✅ Com "id" (conciso):
UUID id = product.getId();
product.setId(uuid);
if (product.getId() != null) { ... }

// ❌ Com "productId" (redundante):
UUID productId = product.getProductId(); // "product" já está no contexto
product.setProductId(uuid);
if (product.getProductId() != null) { ... }
```

---

#### 4. **DTOs e APIs Consistentes**

**REST APIs Padrão (GitHub, Stripe, Shopify, etc.):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "name": "Product A",
  "price": 10.00
}
```

**Com prefixo:**
```json
{
  "productId": "123e4567...", // ← Inconsistente entre domínios
  "name": "Product A"
}
```

**Problema:** Cada domínio tem campo diferente:
- `productId` para Product
- `orderId` para Order
- `userId` para User

**Padrão REST:** Sempre `id`.

---

#### 5. **Compatibilidade Automática com Frameworks**

| Framework/Biblioteca | Comportamento com `id` | Comportamento com `productId` |
|---------------------|------------------------|-------------------------------|
| **Spring Data JPA** | ✅ `findById()` automático | ⚠️ Precisa configurar |
| **Hibernate** | ✅ Otimizado | ⚠️ Configuração extra |
| **Jackson (JSON)** | ✅ Serializa `id` | ⚠️ Pode gerar `productId` ou `id` |
| **MapStruct** | ✅ Mapeia `id` automaticamente | ⚠️ Precisa `@Mapping` |
| **Spring Data REST** | ✅ Expõe `/products/{id}` | ⚠️ Expõe `/products/{productId}` |

---

#### 6. **Padrão de Mercado (90%+ dos Projetos)**

**Projetos Open Source (GitHub):**
- ✅ **Spring PetClinic:** `id`
- ✅ **Spring Data Examples:** `id`
- ✅ **Baeldung Tutorials:** `id`
- ✅ **JHipster (gerador Spring Boot):** `id`
- ✅ **Vlad Mihalcea (especialista Hibernate):** `id`

**Estatística:**
- 90%+ dos projetos Spring Boot usam `id`
- 10% usam `<domain>Id` (geralmente legado ou DDD extremo)

---

### Desvantagens de `id` (e Mitigações)

#### ⚠️ **Ambiguidade em Joins SQL**

**Problema:**
```sql
SELECT p.id, o.id, c.id
FROM products p
JOIN orders o ON p.id = o.product_id
JOIN customers c ON o.customer_id = c.id;
-- ❌ Ambíguo: qual "id"?
```

**Mitigação 1: Usar Aliases SQL**
```sql
SELECT 
    p.id AS product_id, 
    o.id AS order_id, 
    c.id AS customer_id
FROM products p
JOIN orders o ON p.id = o.product_id
JOIN customers c ON o.customer_id = c.id;
-- ✅ Claro, sem mudar código Java
```

**Mitigação 2: JPA Queries com Aliases**
```java
@Query("""
    SELECT p.id AS productId, 
           o.id AS orderId, 
           c.id AS customerId
    FROM ProductEntity p
    JOIN OrderEntity o ON p.id = o.product.id
    JOIN CustomerEntity c ON o.customer.id = c.id
    """)
List<Object[]> findComplexData();
```

**Mitigação 3: Projection DTOs**
```java
public interface ProductOrderProjection {
    UUID getProductId(); // ← Alias no resultado
    UUID getOrderId();
}

@Query("""
    SELECT p.id AS productId, o.id AS orderId
    FROM ProductEntity p JOIN OrderEntity o ON p.id = o.product.id
    """)
List<ProductOrderProjection> findProductOrders();
```

---

## Consequências

### Positivas

✅ **Convenção Spring/JPA:** Segue padrão oficial  
✅ **Menos Verbosidade:** `getId()` vs `getProductId()`  
✅ **Compatibilidade Automática:** Spring Data, Hibernate, Jackson funcionam sem configuração  
✅ **APIs REST Padrão:** Consistente com GitHub, Stripe, etc.  
✅ **Manutenção:** Código mais simples, menos configurações  
✅ **Onboarding:** Desenvolvedores já conhecem o padrão  

### Negativas

⚠️ **Ambiguidade em Joins:** Necessita aliases SQL explícitos  
⚠️ **Não é "self-documenting":** `id` não indica o domínio  

### Mitigações

- Usar aliases SQL quando necessário (`p.id AS product_id`)
- Usar Projection DTOs para queries complexas
- Nomear variáveis Java de forma clara (`UUID productId = product.getId()`)

---

## Implementação

### Template Padrão

```java
@Entity
@Table(name = "products")
public class ProductEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id; // ✅ SEMPRE "id"
    
    // ... outros campos
    
    // Getter e Setter
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
}
```

### Mapeamento para Coluna Diferente (Banco Legado)

Se o banco de dados já existe com nome diferente:

```java
@Entity
@Table(name = "products")
public class ProductEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "product_id", columnDefinition = "UUID") // ← Mapeia para coluna do banco
    private UUID id; // ← Java continua usando "id"
    
    // Getter e Setter
    public UUID getId() { // ← Método continua sendo getId()
        return id;
    }
}
```

**SQL Gerado:**
```sql
SELECT product_id FROM products WHERE product_id = ?;
-- ✅ Usa "product_id" no banco, mas Java usa "id"
```

---

## Aliases SQL em Entidades JPA

### ❌ **Não é Possível Alias Direto no `@Id`**

JPA **não suporta** alias direto na definição da entidade:

```java
@Id
@Column(name = "id", alias = "product_id") // ❌ NÃO EXISTE
private UUID id;
```

**Razão:** `@Column` define o **mapeamento para a tabela**, não para queries.

---

### ✅ **Alternativas para Aliases em Queries**

#### **Opção 1: Aliases em JPQL/HQL**

```java
@Query("""
    SELECT p.id AS productId, 
           p.name AS productName, 
           o.id AS orderId
    FROM ProductEntity p
    LEFT JOIN OrderEntity o ON o.product.id = p.id
    WHERE p.active = true
    """)
List<Object[]> findProductsWithOrders();
```

#### **Opção 2: Projection Interface**

```java
public interface ProductOrderView {
    UUID getProductId(); // ← Alias automático
    String getProductName();
    UUID getOrderId();
}

@Query("""
    SELECT p.id AS productId, 
           p.name AS productName, 
           o.id AS orderId
    FROM ProductEntity p
    LEFT JOIN OrderEntity o ON o.product.id = p.id
    """)
List<ProductOrderView> findProductsWithOrders();

// Uso:
List<ProductOrderView> result = repository.findProductsWithOrders();
result.forEach(view -> {
    System.out.println(view.getProductId()); // ✅ Alias aplicado
});
```

#### **Opção 3: Native Query com `@ColumnResult`**

```java
@SqlResultSetMapping(
    name = "ProductOrderMapping",
    classes = @ConstructorResult(
        targetClass = ProductOrderDTO.class,
        columns = {
            @ColumnResult(name = "product_id", type = UUID.class),
            @ColumnResult(name = "order_id", type = UUID.class)
        }
    )
)
@Entity
public class ProductEntity {
    // ...
}

@Query(value = """
    SELECT p.id AS product_id, o.id AS order_id
    FROM products p
    LEFT JOIN orders o ON o.product_id = p.id
    """, nativeQuery = true)
@SqlResultSetMapping(name = "ProductOrderMapping")
List<ProductOrderDTO> findProductsWithOrdersNative();
```

#### **Opção 4: Hibernate `@Formula` (Coluna Calculada)**

```java
@Entity
public class ProductEntity {
    
    @Id
    private UUID id;
    
    @Formula("id") // ← "Alias" para o próprio ID
    private UUID productId; // Apenas leitura
    
    // Getter
    public UUID getProductId() { // ← Pode usar em DTOs
        return productId;
    }
}
```

**Problema:** Adiciona campo redundante. **Não recomendado** para IDs.

---

### ✅ **Recomendação: Usar Variáveis Java com Nomes Claros**

Em vez de aliases na entidade, use nomes de variáveis descritivos:

```java
// ✅ Bom:
UUID productId = product.getId();
UUID orderId = order.getId();

// ❌ Ruim:
UUID id1 = product.getId();
UUID id2 = order.getId();
```

**Em queries JPQL, use aliases:**
```java
@Query("""
    SELECT p.id AS productId, o.id AS orderId
    FROM ProductEntity p
    JOIN OrderEntity o ON p.id = o.product.id
    """)
List<ProductOrderView> findData();
```

---

## Exemplos Práticos

### Exemplo 1: ProductEntity (Padrão)

```java
@Entity
@Table(name = "products")
public class ProductEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id; // ✅
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private BigDecimal price;
    
    // Getters e Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
}
```

### Exemplo 2: Query com Aliases

```java
public interface ProductOrderView {
    UUID getProductId();
    String getProductName();
    UUID getOrderId();
    LocalDateTime getOrderDate();
}

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    
    @Query("""
        SELECT p.id AS productId, 
               p.name AS productName, 
               o.id AS orderId, 
               o.createdAt AS orderDate
        FROM ProductEntity p
        LEFT JOIN OrderEntity o ON o.product.id = p.id
        WHERE p.active = true
        """)
    List<ProductOrderView> findProductsWithOrders();
}
```

### Exemplo 3: Service com Nomes Claros

```java
@Service
public class ProductService {
    
    public void processOrder(UUID productId, UUID orderId) {
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        
        // ✅ Variáveis com nomes claros
        UUID productPrimaryKey = product.getId();
        UUID orderPrimaryKey = order.getId();
        
        // Lógica de negócio
    }
}
```

---

## Comparação de Abordagens

| Aspecto | `id` | `productId` |
|---------|------|-------------|
| **Convenção JPA/Spring** | ✅ Padrão oficial | ⚠️ Não é padrão |
| **Spring Data JPA** | ✅ Funciona automaticamente | ⚠️ Requer configuração |
| **Verbosidade** | ✅ `getId()` | ❌ `getProductId()` |
| **APIs REST** | ✅ `{ "id": "..." }` | ⚠️ Inconsistente |
| **Joins SQL** | ⚠️ Precisa aliases | ✅ Self-documenting |
| **Padrão de Mercado** | ✅ 90%+ | ❌ 10% |
| **Jackson/MapStruct** | ✅ Automático | ⚠️ Configuração extra |

---

## Alternativas Consideradas

### Alternativa 1: Usar `<domain>Id` (ex: `productId`)

```java
@Entity
public class ProductEntity {
    @Id
    private UUID productId;
}
```

**Decisão:** ❌ Rejeitado
- Não é padrão Spring/JPA
- Mais verboso
- Requer configurações extras
- Inconsistente com APIs REST

---

### Alternativa 2: Usar `<domain>_id` (snake_case)

```java
@Entity
public class ProductEntity {
    @Id
    private UUID product_id; // ❌ Contra convenção Java
}
```

**Decisão:** ❌ Rejeitado
- Contra convenção Java (camelCase)
- Mistura nomenclaturas (Java camelCase, SQL snake_case)

---

### Alternativa 3: Usar `id` (Aceito ✅)

```java
@Entity
public class ProductEntity {
    @Id
    private UUID id; // ✅
}
```

**Decisão:** ✅ **ACEITO**
- Padrão Spring/JPA
- Menos verboso
- Compatível com frameworks
- 90%+ do mercado

---

## Checklist de Code Review

Ao revisar código, verificar:

- [ ] Chave primária é sempre nomeada `id` (não `productId`, `product_id`, etc.)
- [ ] Getter é `getId()` (não `getProductId()`)
- [ ] Setter é `setId(UUID id)` (não `setProductId()`)
- [ ] Se banco usa nome diferente, mapear com `@Column(name = "product_id")`
- [ ] Queries com múltiplas entidades usam aliases (`p.id AS productId`)
- [ ] DTOs usam Projection Interfaces para aliases

---

## Referências

- [Spring Data JPA Reference - Defining Repository Interfaces](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.definition)
- [Jakarta Persistence Specification](https://jakarta.ee/specifications/persistence/3.1/)
- [Hibernate Documentation - Basic Mapping](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#entity-mapping)
- [Baeldung - Spring Data JPA @Query](https://www.baeldung.com/spring-data-jpa-query)
- [Vlad Mihalcea - JPA Best Practices](https://vladmihalcea.com/)

## ADRs Relacionados

- **ADR-001**: UUID como Identificador Padrão (define o tipo do `id`)
- **ADR-005**: Nomenclatura com sufixo "Entity" (define nome da classe)
- **ADR-008**: equals(), hashCode() e toString() (usa `id` para comparação)

## Revisões

| Data | Autor | Mudança |
|------|-------|---------|
| 2025-01-04 | Arquitetura | Decisão inicial aceita |

## Próximos Passos

1. ✅ Aplicado em `ProductEntity` (já usa `id`)
2. Garantir que novas entidades usem `id`
3. Documentar no template de criação de entidades
4. Considerar ArchUnit para validação automatizada

## Metadados

- **Decisores**: Equipe de Arquitetura
- **Impacto**: Médio (Padrão de nomenclatura)
- **Categoria**: Code Standards, JPA, Naming Conventions
- **Tags**: #jpa #entity #primary-key #naming #conventions #spring-data
- **Relacionado**: ADR-001 (UUID), ADR-005 (Nomenclatura), ADR-008 (Equals/HashCode)

