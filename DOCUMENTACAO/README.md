# Índice de Decisões Arquiteturais (ADR)

Este diretório contém os Registros de Decisões Arquiteturais (Architecture Decision Records - ADR) do projeto.

## 📚 Decisões Documentadas

### [ADR-001: UUID como Identificador Padrão](ADR-001-uuid-as-id.md)
**Decisão:** Utilizar UUID (v4) como tipo de identificador para todas as entidades do projeto.

---

### [ADR-002: PagedModel como Padrão de Paginação](ADR-002-pagedmodel-paginacao.md)
**Decisão:** Utilizar `PagedModel` do Spring Data Web como objeto de resposta padrão para todas as operações de listagem paginada.

---

### [ADR-003: Configuração de Limites de Paginação](ADR-003-configuracao-limites-paginacao.md)
**Decisão:** Configurar limites de paginação via properties:
- Tamanho padrão: 20 itens por página
- Tamanho máximo: 100 itens por página
- Paginação zero-based (primeira página = 0)

---

### [ADR-004: YAML como Formato Padrão de Configuração](ADR-004-yaml-formato-padrao.md)
**Decisão:** Utilizar `application.yml` (YAML) como formato padrão para todas as configurações do projeto Spring Boot, substituindo `application.properties`.

---

### [ADR-005: Sufixo "Entity" em Classes de Domínio JPA](ADR-005-sufixo-entity.md)
**Decisão:** Todas as classes anotadas com `@Entity` devem seguir o padrão `<Domain>Entity` (ex: `ProductEntity`, `UserEntity`). Extensão da filosofia para demais camadas:
- `<Domain>Entity` — Entidades JPA
- `<Domain>Repository` — Repositórios Spring Data
- `<Domain>Service` — Serviços de negócio
- `<Domain>Controller` — Controllers REST

---

### [ADR-006: DTOs Separados por Operação](ADR-006-dtos-separados-por-operacao.md)
**Decisão:** Utilizar DTOs específicos para cada operação CRUD:
- `Create<Domain>RequestDTO` — POST (criação)
- `Update<Domain>RequestDTO` — PUT (atualização)
- `<Domain>ResponseDTO` — GET/POST/PUT (resposta)

Campos imutáveis (como `sku`, `id`, `createdAt`) não devem aparecer em `Update<Domain>RequestDTO`.

---

### [ADR-007: Jakarta Validation como Padrão de Validação](ADR-007-jakarta-validation-padrao.md)
**Decisão:** Utilizar Jakarta Validation API (Bean Validation 3.0) com anotações declarativas como padrão único de validação estrutural. Validações aplicadas apenas em DTOs de Request (`Create*RequestDTO`, `Update*RequestDTO`) usando anotações como `@NotBlank`, `@NotNull`, `@Size`, `@Min`, `@Email`, etc. Controller ativa validação com `@Valid`. Regras de negócio complexas permanecem no Service.

---

### [ADR-008: Implementação de equals(), hashCode() e toString() em Entidades JPA](ADR-008-equals-hashcode-tostring-entities.md)
**Decisão:** Todas as entidades JPA devem implementar `equals()` (baseado apenas no ID), `hashCode()` (fixo usando `getClass().hashCode()`) e `toString()` (com campos principais). `equals()` compara apenas a primary key para garantir que duas instâncias representem a mesma linha no banco. `hashCode()` é fixo para permanecer consistente antes e depois de persistir (quando ID muda de `null` para UUID). `toString()` inclui campos principais para debugging legível.

---

### [ADR-009: Nomenclatura de Chave Primária - Usar Apenas "id"](ADR-009-nomenclatura-chave-primaria-id.md)
**Decisão:** Chaves primárias devem sempre se chamar `id` (não `productId`, `product_id`, etc.). Getter/Setter: `getId()` e `setId()`. Se o banco usar nome diferente, mapear com `@Column(name = "product_id")`. Aliases SQL devem ser usados em queries JPQL quando necessário (`p.id AS productId`). Padrão Spring/JPA oficial, adotado por 90%+ do mercado.

---

## 📖 Como Usar Este Índice

Cada ADR contém:
- **Contexto:** Problema/situação que motivou a decisão
- **Decisão:** Solução escolhida
- **Justificativa:** Vantagens, desvantagens e alternativas consideradas
- **Consequências:** Impactos positivos e negativos
- **Implementação:** Exemplos práticos de código

Clique nos links acima para acessar o documento completo de cada decisão.

---

## 🏗️ Sobre ADRs
// ...existing content...

## 📊 Estatísticas

- **Total de ADRs:** 5
- **Aceitos:** 5
- **Propostos:** 0
- **Rejeitados:** 0
- **Última atualização:** 2025-01-04

## 🔗 Relacionamentos entre ADRs

```
ADR-001 (UUID)
   └── ADR-005 (Sufixo Entity) → IDs UUID em todas as entities

ADR-002 (PagedModel)
   └── ADR-003 (Limites) → Configurações complementares
       └── ADR-004 (YAML) → Migração de formato
```

// ...existing content...

## 📂 Organização

```
DOCUMENTACAO/
├── README.md                                    ← Este arquivo
├── ADR-001-uuid-as-id.md                       ← Decisão sobre identificadores
├── ADR-002-pagedmodel-paginacao.md             ← Decisão sobre objeto de paginação
├── ADR-003-configuracao-limites-paginacao.md   ← Decisão sobre limites de paginação
├── ADR-004-yaml-formato-padrao.md              ← Decisão sobre formato de configuração
├── ADR-005-sufixo-entity.md                    ← Decisão sobre nomenclatura de entidades
├── MIGRACAO-YAML.md                            ← Guia de migração YAML
└── prefixo-api.md                              ← Documentação complementar
```

