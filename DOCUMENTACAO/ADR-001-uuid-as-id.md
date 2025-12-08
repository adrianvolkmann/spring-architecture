# ADR 001: Uso de UUID como Identificador Padrão

**Status:** ✅ Aceito  
**Data:** 2025-12-04  
**Decisores:** Equipe de Arquitetura  
**Contexto:** Definição do formato de identificador para entidades do sistema

---

## 📋 Contexto e Problema

O projeto precisa definir um formato padrão para identificadores únicos de todas as entidades (produtos, usuários, pedidos, etc.). A escolha entre **Long (auto-increment)** e **UUID** impacta diretamente:

- Performance de leitura e escrita
- Segurança e privacidade dos dados
- Capacidade de escalabilidade horizontal
- Complexidade de operações e manutenção
- Experiência do desenvolvedor

Esta é uma **decisão estrutural** que afetará todo o ciclo de vida do sistema e é extremamente custosa de reverter após implementação.

---

## 🎯 Decisão

**Adotaremos UUID (versão 4) como formato padrão de identificador para todas as entidades do sistema.**

Formato específico:
- **Tipo:** UUID v4 (random)
- **Armazenamento:** Nativo do PostgreSQL (`UUID`)
- **Representação externa:** String com hífens (formato RFC 4122)
- **Geração:** Pelo banco de dados via `gen_random_uuid()`

---

## 🤔 Motivação

### Razões Principais

#### 1. **Segurança e Privacidade**
Este é um sistema que pode expor dados através de APIs públicas. UUIDs não-sequenciais:
- Impedem enumeração de recursos
- Ocultam volume de dados do negócio
- Dificultam ataques de força bruta
- Protegem privacidade dos usuários

**Exemplo prático:** Um competidor não pode facilmente descobrir quantos produtos temos fazendo `/products/1`, `/products/2`... até encontrar o último.

#### 2. **Preparação para Crescimento**
Embora iniciemos como monolito, prevemos:
- Futura migração para microsserviços
- Possível sharding de banco de dados
- Necessidade de réplicas em múltiplas regiões
- Sincronização entre ambientes (dev/staging/prod)

UUIDs eliminam problemas de colisão de IDs nestes cenários.

#### 3. **Flexibilidade Operacional**
UUIDs facilitam:
- Merge de dados de diferentes ambientes
- Importação de dados externos
- Criação de dados de teste com IDs fixos
- Deploy independente de serviços
- Backup/restore sem preocupação com sequências

#### 4. **Tendência da Indústria**
Sistemas modernos e escaláveis (AWS, GitHub, Stripe) adotam UUIDs ou formatos similares. É o padrão de facto para APIs REST modernas.

---

## ✅ Consequências

### Positivas

#### Performance de Escrita
- ✅ Sem contenção no banco para gerar IDs
- ✅ Inserções paralelas mais eficientes
- ✅ Melhor performance em sistemas distribuídos

#### Segurança
- ✅ Impossível enumerar recursos
- ✅ Não expõe informações de negócio
- ✅ Melhor privacidade de dados

#### Escalabilidade
- ✅ Pronto para microsserviços
- ✅ Facilita sharding futuro
- ✅ Replicação sem conflitos

#### Operações
- ✅ Ambientes independentes
- ✅ Merge de dados facilitado
- ✅ Importações sem conflito

### Negativas

#### Performance de Leitura
- ⚠️ Índices ligeiramente menos eficientes (~10-20% mais lento)
- ⚠️ Maior uso de memória (16 bytes vs 8 bytes)

**Mitigação:** Em bancos modernos (PostgreSQL 13+) o impacto é mínimo. Índices B-tree otimizados para UUID.

#### Experiência do Desenvolvedor
- ⚠️ URLs mais longas
- ⚠️ Difícil copiar/colar manualmente
- ⚠️ Logs mais verbosos

**Mitigação:** Ferramentas modernas (Postman, cURL) lidam bem com UUIDs. Logs podem usar IDs encurtados.

#### Armazenamento
- ⚠️ Maior uso de espaço em disco (~30-40% mais)
- ⚠️ Backups maiores

**Mitigação:** Custo de armazenamento é negligível comparado aos benefícios. Compressão reduz impacto.

---

## 🔧 Detalhes de Implementação

### Banco de Dados (PostgreSQL)

Todas as tabelas seguirão o padrão:

- **Tipo:** `UUID PRIMARY KEY`
- **Default:** `gen_random_uuid()`
- **Índice:** Automático via PRIMARY KEY

### Backend (Spring Boot)

Todas as entidades JPA seguirão:

- **Tipo Java:** `java.util.UUID`
- **Estratégia JPA:** `@GeneratedValue(strategy = GenerationType.UUID)`
- **Column Definition:** `@Column(columnDefinition = "UUID")`

### API REST

Todas as URLs seguirão o formato:

- **Padrão:** `/api/products/{uuid}`
- **Exemplo:** `/api/products/550e8400-e29b-41d4-a716-446655440000`
- **Validação:** Spring converte automaticamente String para UUID

### Frontend

- UUIDs serão tratados como strings opacas
- Nenhuma lógica de negócio baseada em ID
- IDs usados apenas para referência

---

## 🔄 Alternativas Consideradas

### 1. Long (Auto-increment)
**Rejeitada:** Não atende requisitos de segurança e escalabilidade futura.

### 2. ULID (Universally Unique Lexicographically Sortable Identifier)
**Rejeitada:** Complexidade adicional sem benefício claro. UUIDs são mais amplamente suportados.

### 3. Snowflake IDs (Twitter/Discord style)
**Rejeitada:** Requer infraestrutura centralizada de geração. Contra filosofia de descentralização.

### 4. Abordagem Híbrida (Long interno + UUID público)
**Rejeitada:** Complexidade desnecessária. Benefícios não justificam manutenção de dois sistemas.

---

## 📚 Referências

- [RFC 4122 - UUID Specification](https://datatracker.ietf.org/doc/html/rfc4122)
- [PostgreSQL UUID Documentation](https://www.postgresql.org/docs/current/datatype-uuid.html)
- [Spring Data JPA UUID Support](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [UUID vs Auto-increment - Percona](https://www.percona.com/blog/uuids-are-popular-but-bad-for-performance/)
- [GitHub Engineering - Why UUID](https://github.blog/2020-12-04-github-actions-building-a-scalable-infrastructure/)

---

## ✏️ Notas Adicionais

### Monitoramento de Performance

Devemos monitorar:
- Tempo médio de queries com filtros por ID
- Tamanho de índices em disco
- Performance de joins entre tabelas

Se houver degradação significativa (>30%), reavaliaremos a decisão.

### Ponto de Não-Retorno

Após **6 meses em produção** ou **1 milhão de registros**, será extremamente custoso reverter esta decisão. Mudanças devem ser feitas antes deste ponto.

### Revisão Futura

Esta decisão deve ser revisada:
- Se performance se tornar um problema crítico
- Se novos padrões emergirem na indústria (ex: ULID, KSUID)
- Antes de qualquer refatoração arquitetural significativa

---

## 🔐 Aprovação

| Nome | Papel | Decisão | Data |
|------|-------|---------|------|
| Equipe Técnica | Arquiteto de Software | ✅ Aprovado | 2025-12-04 |
| - | Tech Lead | ✅ Aprovado | 2025-12-04 |
| - | Product Owner | ✅ Aprovado | 2025-12-04 |

---

## 📝 Histórico de Mudanças

| Data | Versão | Mudança | Autor |
|------|--------|---------|-------|
| 2025-12-04 | 1.0 | Criação inicial do ADR | Sistema |

---

**Status Final:** ✅ **IMPLEMENTADO**

Esta decisão está ativa e deve ser seguida por todos os desenvolvedores do projeto. Exceções devem ser documentadas e aprovadas pela equipe de arquitetura.

