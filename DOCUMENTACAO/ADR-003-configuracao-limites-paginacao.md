# ADR-003: Configuração de Limites de Paginação

## Status
**Aceito** - 2025-01-04

## Contexto

Com a adoção do `PagedModel` como padrão de paginação (veja [ADR-002](ADR-002-pagedmodel-paginacao.md)), é necessário definir limites e valores padrão para proteger a aplicação contra:

1. **Sobrecarga do banco de dados**: Consultas sem limite de tamanho podem causar queries lentas
2. **Consumo excessivo de memória**: Resultados muito grandes podem esgotar heap memory
3. **Degradação de performance**: Serialização de payloads grandes impacta tempo de resposta
4. **Ataques de negação de serviço**: Requisições maliciosas com `size=999999`

Sem configurações explícitas, o Spring Data permite que clientes solicitem quantidades ilimitadas de registros, o que representa um risco de segurança e performance.

## Decisão

Estabelecemos os seguintes parâmetros de configuração para paginação em `application.properties`:

```properties
# Tamanho padrão da página (quando não especificado pelo cliente)
spring.data.web.pageable.default-page-size=20

# Tamanho máximo permitido por requisição
spring.data.web.pageable.max-page-size=100

# Indexação baseada em zero (page=0 é a primeira página)
spring.data.web.pageable.one-indexed-parameters=false
```

## Justificativa

### 1. Default Page Size: 20 registros

**Análise de padrões de mercado:**
- GitHub API: 30 por página
- Twitter API: 20 por página  
- Google APIs: 25-50 por página
- Stripe API: 10 por página

**Decisão: 20 registros**

✅ **Vantagens:**
- Balanceamento entre performance e usabilidade
- Adequado para interfaces web (1-2 telas de conteúdo)
- Tempo de resposta rápido (< 200ms para queries otimizadas)
- Reduz overhead de múltiplas requisições
- Compatível com experiência mobile

⚠️ **Considerações:**
- Clientes que precisam de mais podem especificar `?size=50`
- Clientes que precisam de menos podem especificar `?size=10`

### 2. Max Page Size: 100 registros

**Análise de riscos:**

| Tamanho | Tempo Estimado* | Memória** | Risco |
|---------|----------------|-----------|-------|
| 50 | ~100ms | ~50KB | Baixo |
| 100 | ~200ms | ~100KB | Médio |
| 500 | ~1s | ~500KB | Alto |
| 1000+ | >2s | >1MB | Crítico |

*Estimativa para queries otimizadas com índices  
**Estimativa para entidades médias (~1KB)

**Decisão: 100 registros**

✅ **Vantagens:**
- Previne requisições abusivas (`?size=999999`)
- Permite casos de uso legítimos (exports, dashboards)
- Limita impacto no banco de dados
- Protege memória heap da aplicação
- Tempo de resposta aceitável (< 500ms)

🔒 **Segurança:**
```
GET /api/products?size=50    ✅ Permitido (dentro do limite)
GET /api/products?size=100   ✅ Permitido (máximo)
GET /api/products?size=500   ⚠️ Limitado automaticamente para 100
GET /api/products?size=99999 ⚠️ Limitado automaticamente para 100
```

### 3. One-Indexed Parameters: false (Zero-Based)

**Comparação de abordagens:**

#### Zero-Based (page=0) - ✅ ESCOLHIDO
```
GET /api/products?page=0&size=20  → Registros 1-20
GET /api/products?page=1&size=20  → Registros 21-40
GET /api/products?page=2&size=20  → Registros 41-60
```

**Vantagens:**
- ✅ Padrão nativo do Spring Data (sem overhead)
- ✅ Consistente com conceitos de programação (arrays começam em 0)
- ✅ Facilita cálculos: `offset = page * size`
- ✅ Alinhado com APIs técnicas (GitHub, GitLab)
- ✅ Sem necessidade de conversões internas

#### One-Based (page=1) - ❌ REJEITADO
```
GET /api/products?page=1&size=20  → Registros 1-20
GET /api/products?page=2&size=20  → Registros 21-40
```

**Desvantagens:**
- ❌ Requer conversão: `offset = (page - 1) * size`
- ❌ Não é padrão Spring Data
- ❌ Overhead de configuração adicional
- ⚠️ Vantagem apenas para UIs (facilmente resolvido no frontend)

**Decisão:** Manter `false` (zero-based) por ser o padrão técnico mais amplamente adotado e nativo do Spring Framework.

## Consequências

### Positivas

✅ **Performance**: Proteção contra queries lentas e consumo excessivo de memória  
✅ **Segurança**: Mitigação de ataques DoS via paginação abusiva  
✅ **Previsibilidade**: Comportamento consistente em todos os endpoints  
✅ **Experiência do Desenvolvedor**: Valores padrão sensatos reduzem necessidade de especificar `size`  
✅ **Observabilidade**: Métricas de paginação mais consistentes  
✅ **Escalabilidade**: Aplicação suporta mais usuários concorrentes  

### Negativas

⚠️ **Limitação**: Clientes que precisam de mais de 100 registros devem fazer múltiplas requisições  
⚠️ **Curva de Aprendizado**: Desenvolvedores podem não conhecer o limite de 100  

### Mitigações

Para casos de uso que necessitam de grandes volumes de dados:

1. **Exportação de Dados**: Implementar endpoint dedicado `/api/products/export`
2. **Streaming**: Usar Server-Sent Events (SSE) ou WebSocket para dados em tempo real
3. **Batch API**: Endpoint específico para requisições batch com autenticação adicional
4. **Cache**: Implementar cache para consultas frequentes

## Implementação

### Arquivo: `application.properties`

```properties
# ============================================================
# Configuração de Paginação
# Decisão técnica: ADR-003
# ============================================================

# Tamanho padrão quando cliente não especifica 'size'
spring.data.web.pageable.default-page-size=20

# Limite máximo de registros por requisição (proteção contra abuso)
spring.data.web.pageable.max-page-size=100

# Indexação baseada em zero (page=0 = primeira página)
# Alinhado com padrões de programação e Spring Data
spring.data.web.pageable.one-indexed-parameters=false
```

### Validação

**Teste 1: Comportamento padrão**
```bash
GET /api/products
# Retorna 20 registros (default-page-size)
```

**Teste 2: Tamanho customizado**
```bash
GET /api/products?size=50
# Retorna 50 registros
```

**Teste 3: Limite máximo**
```bash
GET /api/products?size=500
# Retorna 100 registros (limitado pelo max-page-size)
```

**Teste 4: Zero-based indexing**
```bash
GET /api/products?page=0&size=10
# Retorna registros 1-10 (primeira página)
```

## Documentação da API

Adicionar na documentação Swagger/OpenAPI:

```yaml
parameters:
  - name: page
    in: query
    description: Número da página (zero-based, padrão=0)
    schema:
      type: integer
      minimum: 0
      default: 0
  - name: size
    in: query
    description: Tamanho da página (padrão=20, máximo=100)
    schema:
      type: integer
      minimum: 1
      maximum: 100
      default: 20
```

## Monitoramento

Métricas recomendadas para observar:

- Distribuição de valores de `size` solicitados
- Frequência de requisições com `size=100` (pode indicar necessidade de ajuste)
- Tempo médio de resposta por faixa de tamanho (20, 50, 100)
- Taxa de requisições que atingem o limite máximo

## Revisão Futura

Este ADR deve ser revisado se:

1. Tempo médio de resposta para `size=100` exceder 1 segundo
2. Mais de 10% das requisições solicitarem o máximo (100)
3. Surgir necessidade de endpoint com limite maior
4. Mudanças na arquitetura (ex: migração para NoSQL)

## Alternativas Consideradas

### Alternativa 1: Default=10, Max=50
- **Prós**: Mais conservador, menor risco
- **Contras**: Muitas requisições necessárias, pior UX
- **Decisão**: Rejeitado por ser muito restritivo

### Alternativa 2: Default=50, Max=500
- **Prós**: Menos requisições, melhor para exports
- **Contras**: Risco maior de degradação de performance
- **Decisão**: Rejeitado por potencial impacto em performance

### Alternativa 3: Sem limite máximo
- **Prós**: Máxima flexibilidade
- **Contras**: Alto risco de segurança e performance
- **Decisão**: Rejeitado por questões de segurança

## Referências

- [Spring Data Web - Pagination Configuration](https://docs.spring.io/spring-data/rest/docs/current/reference/html/#paging-and-sorting.pagination)
- [REST API Design - Pagination Best Practices](https://www.baeldung.com/rest-api-pagination-in-spring)
- [OWASP - DoS via Resource Exhaustion](https://owasp.org/www-community/attacks/Denial_of_Service)
- ADR-002: PagedModel como Padrão de Paginação

## Revisões

| Data | Autor | Mudança |
|------|-------|---------|
| 2025-01-04 | Arquitetura | Decisão inicial aceita |

## Metadados

- **Decisores**: Equipe de Arquitetura
- **Impacto**: Médio (Configuração Global)
- **Categoria**: Performance, Security, API Design
- **Tags**: #paginacao #performance #seguranca #limites
- **Relacionado**: ADR-002 (PagedModel)

