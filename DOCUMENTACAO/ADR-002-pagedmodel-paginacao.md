# ADR-002: PagedModel como Padrão de Paginação

## Status
**Aceito** - 2025-01-04

## Contexto

O projeto necessita de um padrão consistente para retornar dados paginados nas APIs REST. O Spring Framework oferece diferentes abordagens para implementar paginação:

1. **`Page<T>`** (Spring Data JPA) - Interface básica com metadados completos
2. **`Slice<T>`** (Spring Data JPA) - Sem informação de total de elementos
3. **`PagedModel<T>`** (Spring Data Web) - Modelo otimizado para respostas REST
4. DTO customizado - Implementação manual

Atualmente o projeto usa `Page<T>` no endpoint principal, mas há inconsistência na estrutura de resposta desejada para APIs REST públicas.

## Decisão

Adotaremos **`PagedModel<T>`** do pacote `org.springframework.data.web` como objeto padrão para todas as respostas paginadas nos endpoints da API REST.

## Justificativa

### Análise Comparativa dos Payloads

#### Page<T> - Payload Atual
```json
{
  "content": [...],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": true, "unsorted": false, "empty": false },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "last": false,
  "totalPages": 5,
  "totalElements": 100,
  "size": 20,
  "number": 0,
  "sort": { "sorted": true, "unsorted": false, "empty": false },
  "first": true,
  "numberOfElements": 20,
  "empty": false
}
```

**Problemas identificados:**
- Estrutura "flat" com muitos campos na raiz do JSON
- Informações duplicadas (`sort` aparece 2x, `pageSize` e `size`)
- Expõe detalhes internos do Spring (`pageable`, `offset`, `paged`, `unpaged`)
- Verbosidade excessiva para consumidores da API
- Não segue padrões REST modernos

#### PagedModel<T> - Payload Proposto
```json
{
  "content": [...],
  "page": {
    "size": 20,
    "totalElements": 100,
    "totalPages": 5,
    "number": 0
  }
}
```

**Vantagens:**
- Estrutura limpa e organizada (metadados agrupados em `page`)
- Apenas informações essenciais para o cliente
- Payload 60-70% menor
- Padrão alinhado com Spring Data REST
- Melhor experiência para desenvolvedores frontend
- Suporte nativo para HATEOAS (links de navegação) se necessário

### Benefícios Técnicos

1. **Clareza**: Separação clara entre conteúdo e metadados
2. **Performance**: Payloads menores = menos tráfego de rede
3. **Manutenibilidade**: Menos campos = menos complexidade
4. **Compatibilidade**: Padrão Spring Data Web amplamente adotado
5. **Extensibilidade**: Fácil adicionar HATEOAS links no futuro

### Exemplo de Uso

```java
@GetMapping
public ResponseEntity<PagedModel<ProductResponseDTO>> findAll(
    @RequestParam(required = false) String name,
    @RequestParam(required = false, defaultValue = "false") boolean onlyActive,
    @PageableDefault(size = 20, sort = "id") Pageable pageable
) {
    Page<ProductResponseDTO> page = productService.findAll(name, onlyActive, pageable);
    return ResponseEntity.ok(new PagedModel<>(page));
}
```

## Consequências

### Positivas
✅ Consistência em todas as APIs de listagem do projeto  
✅ Payloads mais enxutos e fáceis de consumir  
✅ Melhor experiência para desenvolvedores frontend/mobile  
✅ Redução de banda e tempo de serialização  
✅ Facilita testes e mock de dados  
✅ Alinhamento com boas práticas REST  

### Negativas
⚠️ Dependência do pacote `spring-data-commons`  
⚠️ Necessidade de refatorar endpoints existentes que usam `Page<T>`  
⚠️ Mudança de contrato pode impactar clientes existentes (breaking change)  

### Neutras
- Informações como `sort`, `first`, `last` não estarão mais disponíveis no payload
- Clientes que precisam de informações adicionais podem usar query params

## Alternativas Consideradas

### 1. Manter Page<T>
- **Prós**: Sem mudanças, sem breaking changes
- **Contras**: Payload verboso, expõe internals do Spring
- **Decisão**: Rejeitado por não atender requisitos de API limpa

### 2. Slice<T>
- **Prós**: Mais leve que Page
- **Contras**: Não fornece `totalElements` e `totalPages` (essencial para UI)
- **Decisão**: Rejeitado por falta de informações críticas

### 3. DTO Customizado
- **Prós**: Controle total da estrutura
- **Contras**: Requer manutenção manual, reinventar a roda
- **Decisão**: Rejeitado por não agregar valor sobre PagedModel

## Plano de Implementação

### Fase 1: Preparação
- [x] Adicionar dependência `spring-data-commons` (já presente)
- [x] Criar endpoint de teste (`/api/products/adrian`)
- [ ] Validar estrutura de resposta com equipe frontend

### Fase 2: Migração
- [ ] Atualizar endpoint principal `GET /api/products`
- [ ] Atualizar documentação da API (Swagger/OpenAPI)
- [ ] Comunicar breaking change aos consumidores
- [ ] Implementar versionamento de API se necessário

### Fase 3: Padronização
- [ ] Aplicar padrão em todos os endpoints de listagem futuros
- [ ] Criar exemplo de código no guia de desenvolvimento
- [ ] Adicionar validação em code review

## Impacto em Clientes

### Breaking Change
⚠️ Esta mudança quebra o contrato da API para clientes existentes.

**Antes:**
```javascript
const totalPages = response.totalPages;
const isFirst = response.first;
```

**Depois:**
```javascript
const totalPages = response.page.totalPages;
const isFirst = response.page.number === 0;
```

**Mitigação**: 
- Implementar versionamento (ex: `/api/v2/products`)
- OU manter suporte duplo temporário
- OU coordenar deployment com times frontend/mobile

## Referências

- [Spring Data Web - PagedModel](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/web/PagedModel.html)
- [Spring Data REST - Paging and Sorting](https://docs.spring.io/spring-data/rest/docs/current/reference/html/#paging-and-sorting)
- [REST API Design - Pagination Best Practices](https://www.baeldung.com/rest-api-pagination-in-spring)

## Revisões

| Data | Autor | Mudança |
|------|-------|---------|
| 2025-01-04 | Arquitetura | Decisão inicial aceita |

## Metadados

- **Decisores**: Equipe de Arquitetura
- **Impacto**: Alto (Breaking Change)
- **Categoria**: API Design, REST Standards
- **Tags**: #paginacao #rest #api #spring-data

