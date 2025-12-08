# ADR-007: Jakarta Validation como Padrão de Validação

## Status
**Aceito** - 2025-01-04

## Contexto

APIs REST precisam validar dados de entrada para garantir integridade, segurança e consistência. Sem validação adequada, podem ocorrer:

### Problemas Identificados

1. **Dados Inválidos no Banco**: Campos nulos, strings vazias, valores negativos em campos que exigem positivos
2. **Inconsistência**: Validações duplicadas (controller, service, entity)
3. **Manutenibilidade**: Lógica de validação espalhada por múltiplas camadas
4. **Mensagens de Erro**: Falta de padronização nas mensagens de erro
5. **Segurança**: Entrada de dados maliciosos (SQL injection, XSS, etc.)
6. **Experiência do Usuário**: Erros genéricos sem indicar exatamente qual campo está inválido

### Cenário Atual

```java
// Sem validação centralizada - validação manual em Service
@Service
public class ProductService {
    public ProductResponseDTO create(CreateProductRequestDTO dto) {
        // Validações manuais
        if (dto.name() == null || dto.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (dto.price() == null || dto.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        // ... mais validações manuais
        // Lógica de negócio
    }
}
```

**Problemas:**
- ❌ Verboso e repetitivo
- ❌ Difícil manutenção (validações espalhadas)
- ❌ Sem padronização de mensagens
- ❌ Validações podem ser esquecidas

## Decisão

Adotar **Jakarta Validation API (Bean Validation 3.0)** como padrão único de validação para o projeto, utilizando anotações declarativas nos DTOs.

### Especificação

- **Framework:** Jakarta Validation API 3.0+ (JSR-380)
- **Implementação:** Hibernate Validator 8.0+ (implementação de referência)
- **Ativação:** Annotation `@Valid` ou `@Validated` nos parâmetros do Controller
- **Aplicação:** Validações APENAS nos DTOs de entrada (`Create*RequestDTO`, `Update*RequestDTO`)

### Camadas de Validação

| Camada | Tipo de Validação | Ferramenta |
|--------|-------------------|------------|
| **DTO (Request)** | ✅ Estrutural (formato, tipo, range) | Jakarta Validation ✅ |
| **Service** | ✅ Regras de negócio (unicidade, lógica complexa) | Código manual |
| **Entity** | ❌ Nenhuma (confia na validação prévia) | — |
| **Controller** | ❌ Nenhuma (apenas `@Valid` para ativar) | — |

## Justificativa

### Vantagens

#### 1. Declarativo e Conciso

**Antes (validação manual):**
```java
@Service
public class ProductService {
    public ProductResponseDTO create(CreateProductRequestDTO dto) {
        if (dto.name() == null || dto.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (dto.name().length() < 3 || dto.name().length() > 255) {
            throw new IllegalArgumentException("Name must be between 3 and 255 characters");
        }
        if (dto.price() == null) {
            throw new IllegalArgumentException("Price is required");
        }
        if (dto.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        // ... 20 linhas de validação
        
        // Lógica de negócio (3 linhas)
        ProductEntity entity = new ProductEntity();
        entity.setName(dto.name());
        return ProductResponseDTO.fromEntity(productRepository.save(entity));
    }
}
```

**Depois (Jakarta Validation):**
```java
// DTO com validações declarativas
public record CreateProductRequestDTO(
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters")
    String name,
    
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    BigDecimal price
) {}

// Service limpo, focado em lógica de negócio
@Service
public class ProductService {
    public ProductResponseDTO create(CreateProductRequestDTO dto) {
        // Validação estrutural já foi feita pelo Jakarta Validation
        // Apenas lógica de negócio
        ProductEntity entity = new ProductEntity();
        entity.setName(dto.name());
        return ProductResponseDTO.fromEntity(productRepository.save(entity));
    }
}
```

**Resultado:**
- ✅ Service reduzido de 25 linhas para 5 linhas
- ✅ Validações centralizadas no DTO
- ✅ Código de negócio mais legível

#### 2. Padrão da Indústria

**Adotado por:**
- ☕ Java EE / Jakarta EE (especificação oficial)
- 🍃 Spring Boot (integração nativa)
- 🔴 Quarkus
- 🟠 Micronaut
- 🏢 Empresas: Google, Netflix, Amazon, Microsoft

**Especificação:** JSR-380 (Jakarta Bean Validation 3.0)

#### 3. Mensagens de Erro Padronizadas

**Sem Jakarta Validation:**
```json
{
  "error": "Invalid input"  // ❌ Genérico, sem detalhes
}
```

**Com Jakarta Validation + GlobalExceptionHandler:**
```json
{
  "timestamp": "2025-01-04T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "name",
      "rejectedValue": "",
      "message": "Name is required"
    },
    {
      "field": "price",
      "rejectedValue": -10.00,
      "message": "Price must be greater than zero"
    }
  ]
}
```

✅ Cliente sabe exatamente qual campo está inválido e por quê.

#### 4. Integração Automática com Spring Boot

```java
@RestController
public class ProductController {
    
    @PostMapping("/api/products")
    public ResponseEntity<ProductResponseDTO> create(
        @Valid @RequestBody CreateProductRequestDTO dto  // ← @Valid ativa validação
    ) {
        // Se chegou aqui, DTO é válido ✅
        return ResponseEntity.ok(productService.create(dto));
    }
}
```

**Spring automaticamente:**
1. Valida o DTO antes de chamar o método
2. Retorna HTTP 400 com detalhes se inválido
3. Chama `GlobalExceptionHandler` para formatar erro

#### 5. Validações Reutilizáveis

```java
// Validação customizada reutilizável
@Target({ FIELD, PARAMETER })
@Retention(RUNTIME)
@Constraint(validatedBy = SkuValidator.class)
public @interface ValidSku {
    String message() default "Invalid SKU format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Uso
public record CreateProductRequestDTO(
    @ValidSku  // ← Reutilizável em qualquer DTO
    String sku
) {}
```

#### 6. Documentação Automática (Swagger/OpenAPI)

Jakarta Validation é automaticamente refletido na documentação Swagger:

```yaml
# Swagger gerado automaticamente
CreateProductRequestDTO:
  type: object
  required:
    - name
    - price
  properties:
    name:
      type: string
      minLength: 3
      maxLength: 255
    price:
      type: number
      minimum: 0.01
```

✅ Documentação sempre sincronizada com validações reais.

## Consequências

### Positivas

✅ **Concisão**: Menos código, mais legível  
✅ **Manutenibilidade**: Validações centralizadas nos DTOs  
✅ **Padronização**: Mensagens de erro consistentes  
✅ **Documentação Automática**: Swagger reflete validações  
✅ **Reutilização**: Validações customizadas compartilháveis  
✅ **Testabilidade**: Fácil testar validações isoladamente  
✅ **Performance**: Validação antes de chamar Service (fail-fast)  

### Negativas

⚠️ **Curva de Aprendizado**: Equipe precisa conhecer anotações Jakarta Validation  
⚠️ **Validações Complexas**: Regras de negócio complexas ainda precisam ir no Service  
⚠️ **Dependência**: Acoplamento com Hibernate Validator (implementação de referência)  

### Mitigações

```java
// Validações simples: Jakarta Validation no DTO
public record CreateProductRequestDTO(
    @NotBlank String name,
    @DecimalMin("0.01") BigDecimal price
) {}

// Validações complexas: Service
@Service
public class ProductService {
    public ProductResponseDTO create(CreateProductRequestDTO dto) {
        // Validação estrutural já foi feita ✅
        
        // Validação de regra de negócio (complexa)
        if (productRepository.existsByName(dto.name())) {
            throw new BusinessException("Product with this name already exists");
        }
        
        // Lógica de negócio
        // ...
    }
}
```

## Implementação

### 1. Dependência Maven

```xml
<!-- pom.xml -->
<dependencies>
    <!-- Spring Boot Starter Validation (inclui Jakarta Validation + Hibernate Validator) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

### 2. Anotações Disponíveis

#### Validações de String

| Anotação | Descrição | Exemplo |
|----------|-----------|---------|
| `@NotNull` | Campo não pode ser null | `@NotNull String name` |
| `@NotBlank` | String não pode ser null, vazia ou só espaços | `@NotBlank String email` |
| `@NotEmpty` | String/Collection não pode ser null ou vazia | `@NotEmpty List<String> tags` |
| `@Size(min, max)` | Tamanho entre min e max | `@Size(min=3, max=255) String name` |
| `@Pattern(regexp)` | Deve corresponder ao regex | `@Pattern(regexp="^[A-Z0-9-]+$") String sku` |
| `@Email` | Formato de email válido | `@Email String email` |

#### Validações Numéricas

| Anotação | Descrição | Exemplo |
|----------|-----------|---------|
| `@Min(value)` | Valor mínimo (inclusive) | `@Min(0) Integer stockQuantity` |
| `@Max(value)` | Valor máximo (inclusive) | `@Max(1000) Integer quantity` |
| `@DecimalMin(value)` | BigDecimal mínimo | `@DecimalMin("0.01") BigDecimal price` |
| `@DecimalMax(value)` | BigDecimal máximo | `@DecimalMax("999999.99") BigDecimal price` |
| `@Positive` | Número positivo (> 0) | `@Positive BigDecimal price` |
| `@PositiveOrZero` | Número >= 0 | `@PositiveOrZero Integer stock` |
| `@Negative` | Número negativo (< 0) | `@Negative BigDecimal discount` |
| `@NegativeOrZero` | Número <= 0 | `@NegativeOrZero BigDecimal adjustment` |
| `@Digits(integer, fraction)` | Digitos inteiros e fracionários | `@Digits(integer=8, fraction=2) BigDecimal price` |

#### Validações de Data/Hora

| Anotação | Descrição | Exemplo |
|----------|-----------|---------|
| `@Past` | Data no passado | `@Past LocalDate birthDate` |
| `@PastOrPresent` | Data no passado ou hoje | `@PastOrPresent LocalDate createdAt` |
| `@Future` | Data no futuro | `@Future LocalDate expiryDate` |
| `@FutureOrPresent` | Data no futuro ou hoje | `@FutureOrPresent LocalDate scheduledDate` |

#### Validações Booleanas

| Anotação | Descrição | Exemplo |
|----------|-----------|---------|
| `@AssertTrue` | Deve ser true | `@AssertTrue Boolean termsAccepted` |
| `@AssertFalse` | Deve ser false | `@AssertFalse Boolean deleted` |

### 3. Exemplo Completo: CreateProductRequestDTO

```java
package com.volkmann.demo.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * DTO for creating a new Product.
 * Uses Jakarta Validation as per ADR-007.
 */
public record CreateProductRequestDTO(
    
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
    
    Boolean active  // Opcional, valor padrão no Service
) {
}
```

### 4. Controller com @Valid

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    private final ProductService productService;
    
    /**
     * CREATE - Create new product
     * Validation is automatically triggered by @Valid
     */
    @PostMapping
    public ResponseEntity<ProductResponseDTO> create(
        @Valid @RequestBody CreateProductRequestDTO dto  // ← @Valid ativa validação
    ) {
        ProductResponseDTO product = productService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }
    
    /**
     * UPDATE - Update existing product
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateProductRequestDTO dto  // ← @Valid ativa validação
    ) {
        ProductResponseDTO product = productService.update(id, dto);
        return ResponseEntity.ok(product);
    }
}
```

### 5. GlobalExceptionHandler para Formatar Erros

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle Jakarta Validation errors (MethodArgumentNotValidException)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
        MethodArgumentNotValidException ex
    ) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        
        Map<String, String> errors = fieldErrors.stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null 
                    ? error.getDefaultMessage() 
                    : "Invalid value"
            ));
        
        ErrorResponse response = new ErrorResponse(
            LocalDateTime.now(),
            HttpStatus.BAD_REQUEST.value(),
            "Validation Failed",
            errors
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}

// ErrorResponse DTO
public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String message,
    Map<String, String> errors
) {}
```

### 6. Validações Customizadas (Avançado)

```java
// 1. Criar annotation
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SkuValidator.class)
@Documented
public @interface ValidSku {
    String message() default "Invalid SKU format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// 2. Implementar validator
public class SkuValidator implements ConstraintValidator<ValidSku, String> {
    
    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Z0-9]{3}-[A-Z0-9]{3}$");
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;  // Use @NotNull separadamente
        }
        return SKU_PATTERN.matcher(value).matches();
    }
}

// 3. Usar no DTO
public record CreateProductRequestDTO(
    @ValidSku
    @NotNull
    String sku
) {}
```

## Regras de Uso

### ✅ DEVE (Obrigatório)

1. **DTOs de Request DEVEM ter validações Jakarta Validation**
   ```java
   public record CreateProductRequestDTO(
       @NotBlank String name,  // ✅
       @NotNull BigDecimal price  // ✅
   ) {}
   ```

2. **Controller DEVE usar @Valid ou @Validated**
   ```java
   @PostMapping
   public ResponseEntity<?> create(@Valid @RequestBody CreateProductRequestDTO dto) {  // ✅
       // ...
   }
   ```

3. **Mensagens DEVEM ser claras e em inglês**
   ```java
   @NotBlank(message = "Name is required")  // ✅ Clara
   @NotBlank(message = "Invalid")  // ❌ Genérica
   ```

4. **Validações estruturais DEVEM ficar no DTO**
   ```java
   // DTO
   @NotNull
   @Min(0)
   Integer stockQuantity;  // ✅ Validação estrutural no DTO
   ```

5. **Validações de negócio DEVEM ficar no Service**
   ```java
   // Service
   if (productRepository.existsByName(dto.name())) {  // ✅ Regra de negócio no Service
       throw new BusinessException("Product name already exists");
   }
   ```

### ❌ NÃO DEVE (Proibido)

1. **NÃO usar validação manual no Controller**
   ```java
   @PostMapping
   public ResponseEntity<?> create(@RequestBody CreateProductRequestDTO dto) {
       if (dto.name() == null) {  // ❌ ERRADO
           throw new IllegalArgumentException("Name is required");
       }
       // ...
   }
   ```

2. **NÃO colocar Jakarta Validation em Entities**
   ```java
   @Entity
   public class ProductEntity {
       @NotBlank  // ❌ ERRADO - validação deve estar no DTO
       private String name;
   }
   ```

3. **NÃO validar DTOs de Response**
   ```java
   public record ProductResponseDTO(
       @NotNull UUID id  // ❌ ERRADO - response não precisa validação
   ) {}
   ```

4. **NÃO duplicar validações**
   ```java
   // DTO
   @NotBlank String name;  // ✅
   
   // Service
   if (dto.name() == null || dto.name().isBlank()) {  // ❌ DUPLICADO
       throw new IllegalArgumentException("Name is required");
   }
   ```

## Comparação: Validação Manual vs Jakarta Validation

| Aspecto | Validação Manual | Jakarta Validation |
|---------|------------------|-------------------|
| **Linhas de código** | 20-30 linhas | 5-10 anotações |
| **Manutenibilidade** | ❌ Difícil (espalhado) | ✅ Fácil (centralizado) |
| **Padronização** | ❌ Inconsistente | ✅ Consistente |
| **Mensagens de erro** | ❌ Genéricas | ✅ Específicas por campo |
| **Documentação Swagger** | ❌ Manual | ✅ Automática |
| **Testabilidade** | ❌ Difícil | ✅ Fácil |
| **Performance** | ⚠️ Após chamar Service | ✅ Antes de chamar Service |
| **Reutilização** | ❌ Duplicação | ✅ Anotações reutilizáveis |
| **Padrão da indústria** | ❌ Não | ✅ Sim (JSR-380) |

## Exemplos Práticos por Tipo

### String

```java
public record UserRequestDTO(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password,
    
    @Pattern(regexp = "^[A-Za-z ]+$", message = "Name must contain only letters")
    String name
) {}
```

### Numérico

```java
public record ProductRequestDTO(
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be positive")
    @Digits(integer = 8, fraction = 2)
    BigDecimal price,
    
    @Min(value = 0, message = "Stock cannot be negative")
    @Max(value = 10000, message = "Stock cannot exceed 10000")
    Integer stockQuantity
) {}
```

### Data/Hora

```java
public record EventRequestDTO(
    @NotNull(message = "Event date is required")
    @Future(message = "Event date must be in the future")
    LocalDateTime eventDate,
    
    @PastOrPresent(message = "Registration date cannot be in the future")
    LocalDate registrationDate
) {}
```

### Collections

```java
public record OrderRequestDTO(
    @NotEmpty(message = "Order must have at least one item")
    @Size(min = 1, max = 100, message = "Order can have between 1 and 100 items")
    List<@Valid OrderItemDTO> items  // ← @Valid valida cada item da lista
) {}
```

### Nested Objects

```java
public record CreateOrderRequestDTO(
    @NotNull(message = "Customer is required")
    @Valid  // ← Valida objeto aninhado
    CustomerDTO customer,
    
    @NotEmpty(message = "Items are required")
    List<@Valid OrderItemDTO> items  // ← Valida cada item
) {}

public record CustomerDTO(
    @NotBlank String name,
    @Email String email
) {}
```

## Testes

### Teste de Validação Isolada

```java
@Test
void shouldRejectInvalidProduct() {
    // Arrange
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    
    CreateProductRequestDTO dto = new CreateProductRequestDTO(
        "",  // Nome vazio (inválido)
        "Description",
        BigDecimal.valueOf(-10),  // Preço negativo (inválido)
        -5,  // Estoque negativo (inválido)
        true
    );
    
    // Act
    Set<ConstraintViolation<CreateProductRequestDTO>> violations = validator.validate(dto);
    
    // Assert
    assertThat(violations).hasSize(3);
    assertThat(violations)
        .extracting(ConstraintViolation::getMessage)
        .containsExactlyInAnyOrder(
            "Name is required",
            "Price must be greater than zero",
            "Stock quantity must be greater than or equal to zero"
        );
}
```

### Teste de Controller com Validação

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ProductService productService;
    
    @Test
    void shouldReturn400WhenProductNameIsBlank() throws Exception {
        // Arrange
        String invalidJson = """
            {
                "name": "",
                "price": 10.00,
                "stockQuantity": 5
            }
            """;
        
        // Act & Assert
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").value("Name is required"));
    }
}
```

## Referências

- [Jakarta Bean Validation 3.0 Specification](https://jakarta.ee/specifications/bean-validation/3.0/)
- [Hibernate Validator Documentation](https://hibernate.org/validator/)
- [Spring Boot Validation Guide](https://spring.io/guides/gs/validating-form-input/)
- [Baeldung - Spring Boot Bean Validation](https://www.baeldung.com/spring-boot-bean-validation)
- [JSR-380 (Bean Validation 2.0)](https://jcp.org/en/jsr/detail?id=380)

## ADRs Relacionados

- **ADR-006**: DTOs Separados por Operação (validações aplicadas em Create/Update DTOs)
- **ADR-005**: Nomenclatura com sufixos (DTOs recebem validações)

## Revisões

| Data | Autor | Mudança |
|------|-------|---------|
| 2025-01-04 | Arquitetura | Decisão inicial aceita |

## Próximos Passos

1. ✅ Adicionar `spring-boot-starter-validation` no pom.xml (já incluído)
2. ✅ Aplicar validações em `CreateProductRequestDTO` e `UpdateProductRequestDTO`
3. ⏳ Aplicar em novos DTOs de outros domínios
4. ⏳ Criar validações customizadas conforme necessário
5. ⏳ Adicionar testes de validação

## Metadados

- **Decisores**: Equipe de Arquitetura
- **Impacto**: Alto (Afeta todos os DTOs de Request)
- **Categoria**: Code Standards, Validation, API Quality
- **Tags**: #validation #jakarta #bean-validation #dto #api-quality #standards
- **Relacionado**: ADR-006 (DTOs Separados), ADR-005 (Nomenclatura)

