# ADR-008: Implementação de equals(), hashCode() e toString() em Entidades JPA

## Status
**Aceito** - 2025-01-04

## Contexto

Entidades JPA (`@Entity`) são classes que representam tabelas no banco de dados. Por padrão, classes Java herdam implementações de `equals()`, `hashCode()` e `toString()` da classe `Object`, que **não são adequadas** para entidades JPA.

### Problemas Identificados

#### 1. **`equals()` Padrão (Compara Referência de Memória)**

```java
// Implementação padrão de Object.equals()
public boolean equals(Object obj) {
    return (this == obj); // Compara apenas ponteiros de memória
}
```

**Problemas:**

**a) Collections não funcionam corretamente:**
```java
ProductEntity p1 = productRepository.findById(uuid).orElseThrow();
ProductEntity p2 = productRepository.findById(uuid).orElseThrow(); // Mesma linha no banco

p1.equals(p2); // ❌ FALSE (são instâncias diferentes na memória)
// Mas representam a MESMA entidade no banco!
```

**b) Hibernate Collections (`Set<ProductEntity>`) quebram:**
```java
@Entity
public class CategoryEntity {
    @OneToMany(mappedBy = "category")
    private Set<ProductEntity> products = new HashSet<>();
}

// Ao carregar e adicionar produtos ao Set:
category.getProducts().add(product1);
category.getProducts().add(product1); // ❌ Pode adicionar duplicata!
```

**c) Merge de entidades detached falha:**
```java
ProductEntity detached = productRepository.findById(uuid).orElseThrow();
// detached é desanexado da sessão

detached.setPrice(new BigDecimal("20.00"));

ProductEntity managed = productRepository.findById(uuid).orElseThrow();
// managed é nova instância

detached.equals(managed); // ❌ FALSE (deveria ser TRUE)
```

---

#### 2. **`hashCode()` Padrão (Hash Baseado em Memória)**

```java
// Implementação padrão de Object.hashCode()
public int hashCode() {
    return System.identityHashCode(this); // Hash baseado no endereço de memória
}
```

**Problemas:**

**a) HashSet/HashMap não funcionam:**
```java
ProductEntity product = productRepository.findById(uuid).orElseThrow();
Set<ProductEntity> set = new HashSet<>();
set.add(product);

product.setPrice(new BigDecimal("15.00")); // Altera estado

set.contains(product); // ⚠️ Pode retornar FALSE!
```

**b) Quebra o contrato Java:**
> **Contrato oficial:** Se `a.equals(b) == true`, então **DEVE** `a.hashCode() == b.hashCode()`

Sem sobrescrever ambos, estruturas como `HashMap`, `HashSet`, `LinkedHashMap` **não funcionam**.

**c) Cache L2 do Hibernate falha:**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
```

Hibernate usa `hashCode()` para gerenciar cache. Sem implementação, o cache **não funciona corretamente**.

---

#### 3. **`toString()` Padrão (Ilegível)**

```java
// Implementação padrão de Object.toString()
public String toString() {
    return getClass().getName() + "@" + Integer.toHexString(hashCode());
}
```

**Problemas:**

**a) Logs ilegíveis:**
```java
logger.info("Product: {}", product);
// Output: ProductEntity@4b1210ee  ❌ INÚTIL!
```

**b) Debugging difícil:**
```
ProductEntity@4b1210ee ← Precisa expandir TODOS os campos manualmente
```

**c) Exception messages ruins:**
```java
throw new IllegalArgumentException("Invalid product: " + product);
// Output: Invalid product: ProductEntity@4b1210ee  ❌ Sem contexto!
```

---

## Decisão

Todas as entidades JPA (`@Entity`) **DEVEM** implementar `equals()`, `hashCode()` e `toString()` seguindo o padrão:

### 1. **`equals()` — Baseado APENAS no ID**

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProductEntity that = (ProductEntity) o;
    return id != null && Objects.equals(id, that.id);
}
```

**Razão:** ID é a **identidade única** da entidade no banco de dados (primary key).

---

### 2. **`hashCode()` — Fixo (Baseado na Classe)**

```java
@Override
public int hashCode() {
    return getClass().hashCode();
}
```

**Razão:** `hashCode()` **DEVE ser consistente** antes e depois de persistir (quando `id` muda de `null` para UUID).

---

### 3. **`toString()` — Campos Principais**

```java
@Override
public String toString() {
    return "ProductEntity{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", price=" + price +
            ", active=" + active +
            '}';
}
```

**Razão:** Facilita debugging e logs legíveis.

---

## Justificativa

### Por Que `equals()` Compara APENAS o ID?

#### ✅ **ID é a Identidade Única no Banco**

```sql
-- Mesma linha no banco = mesma entidade
SELECT * FROM products WHERE id = '123e4567-e89b-12d3-a456-426614174000';
```

Se dois objetos têm o **mesmo ID**, eles representam a **mesma linha no banco**, independente dos valores de outros campos.

#### ❌ **Comparar Todos os Campos Causa Problemas**

**Problema 1: Entidade muda após update**
```java
ProductEntity product = productRepository.findById(uuid).orElseThrow();
Set<ProductEntity> set = new HashSet<>();
set.add(product);

product.setPrice(new BigDecimal("20.00")); // Atualiza campo
set.contains(product); // ❌ FALSE (com equals baseado em campos)
                       // ✅ TRUE (com equals baseado em ID)
```

**Problema 2: Hibernate Proxy**
```java
ProductEntity product1 = productRepository.findById(uuid).orElseThrow();
ProductEntity product2 = productRepository.getById(uuid); // Lazy proxy

product1.equals(product2); // ❌ FALSE (campos podem não estar carregados)
                           // ✅ TRUE (com equals baseado em ID)
```

---

### Por Que `hashCode()` é Fixo?

#### ❌ **Problema: ID é `null` Antes de Persistir**

```java
ProductEntity product = new ProductEntity("Product A", null, new BigDecimal("10.00"), 100);

// Antes de persistir:
product.getId(); // null ❌
product.hashCode(); // ??? (não pode depender de id == null)

productRepository.save(product);

// Depois de persistir:
product.getId(); // 123e4567-e89b-12d3-a456-426614174000 ✅
product.hashCode(); // ??? (DEVE SER O MESMO de antes!)
```

**Regra do Java:**
> Se você adiciona um objeto a um `HashSet`, **o `hashCode()` NÃO PODE MUDAR**. Caso contrário, o `Set` perde o objeto.

#### ❌ **Abordagem ERRADA (baseada em ID):**

```java
@Override
public int hashCode() {
    return Objects.hash(id); // ❌ ERRADO!
}
```

**Problema:**
```java
ProductEntity product = new ProductEntity("Product A", null, new BigDecimal("10.00"), 100);
Set<ProductEntity> products = new HashSet<>();

products.add(product); // hashCode() = hash(null) = 0
System.out.println(products.contains(product)); // true ✅

productRepository.save(product); // Agora id = 123e4567...

System.out.println(products.contains(product)); // FALSE ❌❌❌
```

**Por quê?**
1. Antes: `hashCode() = hash(null) = 0`
2. Depois: `hashCode() = hash(123e4567...) = 123456789` (mudou!)
3. `HashSet` procura no bucket errado → **não encontra o objeto!**

#### ✅ **Solução: `getClass().hashCode()` (Sempre Fixo)**

```java
@Override
public int hashCode() {
    return getClass().hashCode(); // Sempre retorna o MESMO valor
}
```

**Vantagens:**

**1. HashCode consistente (sempre o mesmo):**
```java
ProductEntity p1 = new ProductEntity("Product A", null, new BigDecimal("10.00"), 100);
ProductEntity p2 = new ProductEntity("Product B", null, new BigDecimal("20.00"), 200);

p1.hashCode(); // 123456 (hash de ProductEntity.class)
p2.hashCode(); // 123456 (MESMO VALOR)

productRepository.save(p1);
productRepository.save(p2);

p1.hashCode(); // 123456 (NÃO MUDOU ✅)
p2.hashCode(); // 123456 (NÃO MUDOU ✅)
```

**2. `equals()` diferencia, `hashCode()` não:**
```java
ProductEntity p1 = productRepository.findById(uuid1).orElseThrow();
ProductEntity p2 = productRepository.findById(uuid2).orElseThrow();

p1.equals(p2); // false (IDs diferentes) ✅
p1.hashCode() == p2.hashCode(); // true (ambos são ProductEntity) ✅
```

**Isso funciona porque:**
- `hashCode()` é usado para **particionar** objetos em buckets do `HashMap`/`HashSet`
- `equals()` é usado para **verificar igualdade real** dentro do bucket
- **É aceitável** ter objetos diferentes com o mesmo `hashCode()` (colisão de hash)

**Performance:**
- ⚠️ Todos os objetos no mesmo bucket (colisão de hash)
- ✅ Mas `equals()` ainda diferencia corretamente
- ✅ Para aplicações típicas (< 10.000 objetos no Set), não há impacto perceptível

---

### Por Que `toString()` Inclui Campos Principais?

#### ✅ **Logs Legíveis**

**Antes:**
```java
logger.info("Product: {}", product);
// Output: ProductEntity@4b1210ee  ❌
```

**Depois:**
```java
logger.info("Product: {}", product);
// Output: ProductEntity{id=123e4567..., name='Product A', price=10.00, active=true}  ✅
```

#### ✅ **Debugging Facilitado**

No debugger da IDE:
```
ProductEntity{id=123e4567..., name='Product A', price=10.00, active=true}
```

#### ✅ **Exception Messages Claras**

```java
throw new IllegalArgumentException("Invalid product: " + product);
// Output: Invalid product: ProductEntity{id=123e4567..., name='Product A', ...}  ✅
```

---

## Consequências

### Positivas

✅ **Collections Funcionam:** `HashSet<ProductEntity>`, `HashMap<ProductEntity, ?>` funcionam corretamente  
✅ **Hibernate Collections:** Relacionamentos `@OneToMany` com `Set` funcionam  
✅ **Merge/Detach:** Hibernate detecta entidades iguais corretamente  
✅ **Cache L2:** Funciona corretamente se habilitado  
✅ **Contrato Java:** `equals()` e `hashCode()` respeitam o contrato  
✅ **Logs Legíveis:** `toString()` facilita debugging  
✅ **Padrão da Indústria:** Seguido por Spring, Hibernate, Jakarta EE  

### Negativas

⚠️ **HashCode Fixo:** Todos os objetos da mesma classe têm o mesmo `hashCode()` (colisão de hash)  
⚠️ **Performance:** Pequena degradação em collections muito grandes (> 10.000 objetos)  

### Mitigações

- Para aplicações típicas, não há impacto perceptível
- `equals()` baseado em ID ainda diferencia objetos corretamente

---

## Implementação

### Template Obrigatório para Entidades JPA

```java
@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private String name;
    private BigDecimal price;
    // ... outros campos
    
    // Getters e Setters
    // ...

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductEntity that = (ProductEntity) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ProductEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", active=" + active +
                '}';
    }
}
```

### Regras de Implementação

#### `equals()`

**DEVE:**
1. Comparar `this == o` primeiro (otimização)
2. Verificar `o == null` e `getClass() != o.getClass()`
3. Comparar **APENAS o ID** (primary key)
4. Verificar `id != null` (entidades não persistidas não são iguais)

**NÃO DEVE:**
- Comparar outros campos além do ID
- Usar `instanceof` (problemas com Hibernate Proxy)
- Usar `id.equals()` sem verificar `id != null` primeiro

#### `hashCode()`

**DEVE:**
1. Retornar `getClass().hashCode()` (fixo)
2. **NÃO** usar `Objects.hash(id)` ou campos mutáveis

**Razão:** `hashCode()` **NÃO PODE MUDAR** durante o ciclo de vida do objeto.

#### `toString()`

**DEVE:**
1. Incluir o nome da classe
2. Incluir o ID
3. Incluir campos principais (name, price, active, etc.)
4. **NÃO** incluir relacionamentos `@OneToMany`, `@ManyToMany` (causa Lazy Loading Exception)
5. Truncar campos grandes (ex: description > 50 caracteres)

**Formato recomendado:**
```java
"EntityName{id=..., field1='...', field2=..., ...}"
```

---

## Exemplos Práticos

### Exemplo 1: ProductEntity

```java
@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Getters e Setters
    // ...

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductEntity that = (ProductEntity) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ProductEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + (description != null && description.length() > 50 
                    ? description.substring(0, 50) + "..." 
                    : description) + '\'' +
                ", price=" + price +
                ", stockQuantity=" + stockQuantity +
                ", active=" + active +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
```

### Exemplo 2: UserEntity

```java
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String email;
    private String name;
    private Boolean active;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserEntity that = (UserEntity) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "UserEntity{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", active=" + active +
                '}';
    }
}
```

### Exemplo 3: Entity com Relacionamento (Cuidado com toString!)

```java
@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @OneToMany(mappedBy = "category")
    private Set<ProductEntity> products = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CategoryEntity that = (CategoryEntity) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "CategoryEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", productsCount=" + (products != null ? products.size() : 0) +
                '}';
        // ⚠️ NÃO incluir `products` diretamente (causa Lazy Loading Exception)
    }
}
```

---

## Comparação de Abordagens

| Abordagem | `equals()` | `hashCode()` | Consistência | Problemas |
|-----------|-----------|-------------|--------------|-----------|
| **Padrão (Object)** ❌ | Referência | Memória | ❌ Não funciona | Collections quebram |
| **Todos os campos** ❌ | Campos | `hash(campos)` | ❌ Muda após update | Collections quebram |
| **ID apenas (hash mutável)** ❌ | ID | `hash(id)` | ❌ Muda ao persistir | Collections quebram |
| **ID + hashCode fixo** ✅ | ID | `getClass().hashCode()` | ✅ **Sempre consistente** | ✅ Funciona |

---

## Testes

### Teste 1: Equals Baseado em ID

```java
@Test
void shouldBeEqualWhenSameId() {
    UUID id = UUID.randomUUID();
    
    ProductEntity p1 = new ProductEntity("Product A", null, new BigDecimal("10.00"), 100);
    p1.setId(id);
    
    ProductEntity p2 = new ProductEntity("Product B", null, new BigDecimal("99.99"), 999);
    p2.setId(id);
    
    assertEquals(p1, p2); // ✅ Mesmo ID = iguais
}

@Test
void shouldNotBeEqualWhenDifferentId() {
    ProductEntity p1 = new ProductEntity("Product A", null, new BigDecimal("10.00"), 100);
    p1.setId(UUID.randomUUID());
    
    ProductEntity p2 = new ProductEntity("Product A", null, new BigDecimal("10.00"), 100);
    p2.setId(UUID.randomUUID());
    
    assertNotEquals(p1, p2); // ✅ IDs diferentes = não iguais
}

@Test
void shouldNotBeEqualWhenIdIsNull() {
    ProductEntity p1 = new ProductEntity("Product A", null, new BigDecimal("10.00"), 100);
    ProductEntity p2 = new ProductEntity("Product A", null, new BigDecimal("10.00"), 100);
    
    assertNotEquals(p1, p2); // ✅ Entidades não persistidas não são iguais
}
```

### Teste 2: HashCode Consistente

```java
@Test
void hashCodeShouldBeConsistentBeforeAndAfterPersist() {
    ProductEntity product = new ProductEntity("Product A", null, new BigDecimal("10.00"), 100);
    
    int hashBeforePersist = product.hashCode();
    
    productRepository.save(product);
    
    int hashAfterPersist = product.hashCode();
    
    assertEquals(hashBeforePersist, hashAfterPersist); // ✅ HashCode não mudou
}

@Test
void shouldWorkWithHashSet() {
    ProductEntity product = new ProductEntity("Product A", null, new BigDecimal("10.00"), 100);
    Set<ProductEntity> set = new HashSet<>();
    
    set.add(product);
    assertTrue(set.contains(product)); // ✅
    
    productRepository.save(product);
    
    assertTrue(set.contains(product)); // ✅ Ainda funciona após persistir
}
```

### Teste 3: ToString Legível

```java
@Test
void toStringShouldIncludeMainFields() {
    ProductEntity product = new ProductEntity("Product A", "Description", new BigDecimal("10.00"), 100);
    product.setId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    
    String toString = product.toString();
    
    assertThat(toString).contains("ProductEntity");
    assertThat(toString).contains("id=123e4567");
    assertThat(toString).contains("name='Product A'");
    assertThat(toString).contains("price=10.00");
}
```

---

## Alternativas Consideradas

### Alternativa 1: Usar Lombok `@EqualsAndHashCode`

```java
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProductEntity {
    @Id
    @EqualsAndHashCode.Include
    private UUID id;
}
```

**Decisão:** ❌ Rejeitado
- Lombok usa `Objects.hash(id)` por padrão (hashCode mutável)
- Mesmo configurando `@EqualsAndHashCode(cacheStrategy = CacheStrategy.LAZY)`, não resolve o problema do `id == null`

### Alternativa 2: UUID Gerado no Construtor

```java
@Id
private UUID id = UUID.randomUUID(); // Gerado aqui, não no banco

@Override
public int hashCode() {
    return Objects.hash(id); // Funciona porque id nunca é null
}
```

**Decisão:** ❌ Rejeitado
- Perde `@GeneratedValue(strategy = GenerationType.UUID)` do banco
- UUID gerado em Java (não no PostgreSQL)
- Não segue ADR-001 (UUID como ID padrão)

### Alternativa 3: Business Key (Natural Key)

```java
@Override
public boolean equals(Object o) {
    // ...
    return Objects.equals(sku, that.sku); // sku é único
}

@Override
public int hashCode() {
    return Objects.hash(sku);
}
```

**Decisão:** ❌ Rejeitado
- Requer campo único (constraint `UNIQUE` no banco)
- ProductEntity não tem campo único além do ID
- Business key pode mudar (ex: renomear SKU)

### Alternativa 4: ID + hashCode Fixo ✅

```java
@Override
public boolean equals(Object o) {
    // ...
    return id != null && Objects.equals(id, that.id);
}

@Override
public int hashCode() {
    return getClass().hashCode();
}
```

**Decisão:** ✅ **ACEITO**
- HashCode consistente (não muda)
- Funciona com Hibernate
- Padrão da indústria (Vlad Mihalcea, Thorben Janssen)

---

## Checklist de Code Review

Ao revisar código, verificar:

- [ ] Entidade tem `@Override public boolean equals(Object o)`
- [ ] `equals()` compara **APENAS o ID** (primary key)
- [ ] `equals()` verifica `id != null`
- [ ] Entidade tem `@Override public int hashCode()`
- [ ] `hashCode()` retorna `getClass().hashCode()` (fixo)
- [ ] `hashCode()` **NÃO** usa `Objects.hash(id)` ou campos mutáveis
- [ ] Entidade tem `@Override public String toString()`
- [ ] `toString()` inclui nome da classe e campos principais
- [ ] `toString()` **NÃO** inclui relacionamentos `@OneToMany`, `@ManyToMany`
- [ ] Import de `java.util.Objects` presente

---

## Referências

- [Vlad Mihalcea - Best Way to Implement Equals/HashCode with JPA and Hibernate](https://vladmihalcea.com/the-best-way-to-implement-equals-hashcode-and-tostring-with-jpa-and-hibernate/)
- [Thorben Janssen - Ultimate Guide to Implementing Equals and HashCode with Hibernate](https://thorben-janssen.com/ultimate-guide-to-implementing-equals-and-hashcode-with-hibernate/)
- [Hibernate Documentation - Equals and HashCode](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#mapping-model-pojo-equalshashcode)
- [Effective Java (Joshua Bloch) - Item 10: Obey the general contract when overriding equals](https://www.oreilly.com/library/view/effective-java/9780134686097/)
- [Java Persistence with Hibernate (Christian Bauer) - Section 3.3.4: Identity and Equality](https://www.manning.com/books/java-persistence-with-hibernate-second-edition)

## ADRs Relacionados

- **ADR-001**: UUID como Identificador Padrão (usado no `equals()`)
- **ADR-005**: Nomenclatura com sufixo "Entity" (aplicado em todas as entidades)

## Revisões

| Data | Autor | Mudança |
|------|-------|---------|
| 2025-01-04 | Arquitetura | Decisão inicial aceita e aplicada em ProductEntity |

## Próximos Passos

1. ✅ Aplicar em `ProductEntity`
2. Aplicar em futuras entidades
3. Adicionar ao template de criação de entidades
4. Considerar ArchUnit para validação automatizada

## Metadados

- **Decisores**: Equipe de Arquitetura
- **Impacto**: Alto (Afeta todas as entidades JPA)
- **Categoria**: Code Standards, JPA, Best Practices
- **Tags**: #jpa #entity #equals #hashcode #tostring #hibernate #collections
- **Relacionado**: ADR-001 (UUID), ADR-005 (Nomenclatura)

