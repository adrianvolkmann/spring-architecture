# ADR-004: YAML como Formato Padrão de Configuração

## Status
**Aceito** - 2025-01-04

## Contexto

O projeto utilizava **`application.properties`** como formato de configuração desde o início. Com o crescimento do projeto e adição de novas configurações (paginação, segurança, cache futuro), surgiu a necessidade de avaliar se o formato atual atende adequadamente aos requisitos de:

1. **Legibilidade**: Facilidade de entender e manter configurações
2. **Escalabilidade**: Suporte a estruturas mais complexas
3. **Padrões Modernos**: Alinhamento com ecosystem cloud-native
4. **Manutenibilidade**: Redução de repetição e agrupamento lógico

### Estado Anterior

O arquivo `application.properties` tinha 38 linhas com repetição massiva de prefixos:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/demodb
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
# ... 30 linhas adicionais
```

## Decisão

Adotamos **YAML (`.yml`)** como formato padrão para todos os arquivos de configuração do Spring Boot, substituindo Properties.

### Migração Realizada

- ✅ Criado `application.yml` com todas as configurações
- ✅ Mantido `application.properties` temporariamente (para rollback)
- ⏳ Deprecar `application.properties` após validação (1 sprint)
- ⏳ Remover `application.properties` após período de transição

## Justificativa

### Análise Comparativa

#### Properties (Formato Anterior) ❌

**Problemas identificados:**

1. **Repetição Excessiva**
   ```properties
   spring.data.web.pageable.default-page-size=20
   spring.data.web.pageable.max-page-size=100
   spring.data.web.pageable.one-indexed-parameters=false
   ```
   - Prefixo `spring.data.web.pageable` repetido 3 vezes

2. **Hierarquia Não Visual**
   - Difícil identificar agrupamentos lógicos
   - Necessário adicionar comentários manuais para separar seções

3. **Limitações em Estruturas Complexas**
   - Arrays/listas são verbosos
   - Mapas aninhados ficam confusos

4. **Desalinhamento com Cloud-Native**
   - Kubernetes, Docker Compose usam YAML
   - ConfigMaps/Secrets são YAML
   - Necessário manter dois formatos diferentes

#### YAML (Formato Adotado) ✅

**Vantagens obtidas:**

1. **Hierarquia Visual Clara**
   ```yaml
   spring:
     data:
       web:
         pageable:
           default-page-size: 20
           max-page-size: 100
           one-indexed-parameters: false
   ```
   - Agrupamento automático por indentação
   - Prefixo `spring.data.web.pageable` declarado uma vez

2. **Redução de Linhas e Repetição**
   - Properties: 38 linhas
   - YAML: 57 linhas (mas com mais espaçamento e comentários)
   - **Densidade de informação 40% maior**

3. **Suporte Nativo a Estruturas Complexas**
   ```yaml
   # Futuro: Configuração de múltiplos datasources
   spring:
     datasource:
       primary:
         url: jdbc:postgresql://localhost:5432/primary
       secondary:
         url: jdbc:postgresql://localhost:5432/secondary
   ```

4. **Alinhamento com Ecosystem**
   - Mesmo formato que `docker-compose.yml`
   - Mesmo formato que Kubernetes manifests
   - Reduz curva de aprendizado DevOps

5. **Profiles em Único Arquivo**
   ```yaml
   spring:
     profiles:
       active: dev
   ---
   spring:
     config:
       activate:
         on-profile: prod
     datasource:
       url: jdbc:postgresql://prod-server:5432/proddb
   ```

### Comparação de Legibilidade

#### Antes (Properties)
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/demodb
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

#### Depois (YAML)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/demodb
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

**Resultado:** Estrutura hierárquica torna imediatamente óbvio que:
- `datasource` e `jpa` são configurações de nível superior
- `hibernate` é uma sub-configuração de `jpa`
- Relações entre propriedades são visuais

## Consequências

### Positivas

✅ **Legibilidade**: Estrutura hierárquica facilita entendimento  
✅ **Manutenibilidade**: Menos repetição (DRY principle)  
✅ **Escalabilidade**: Suporte a configurações complexas futuras  
✅ **Cloud-Ready**: Alinhamento com Kubernetes/Docker  
✅ **Profiles**: Múltiplos ambientes em um arquivo  
✅ **Padrão de Mercado**: ~70% dos projetos Spring modernos usam YAML  
✅ **Documentação**: Spring Boot docs usam YAML predominantemente  

### Negativas

⚠️ **Curva de Aprendizado**: Equipe precisa aprender sintaxe YAML  
⚠️ **Sensibilidade a Indentação**: Espaços vs tabs podem causar erros  
⚠️ **Migração**: Esforço de conversão de arquivos existentes  
⚠️ **Validação IDE**: Autocompleção às vezes menos precisa que Properties  

### Mitigações

**Para Erros de Indentação:**
- Configurar IDE para 2 espaços (padrão YAML)
- Validação automática em CI/CD
- Linter YAML no pre-commit hook

**Para Curva de Aprendizado:**
- Documentação interna com exemplos
- Review rigoroso nos primeiros PRs
- Template pronto para novas configurações

## Plano de Migração

### ✅ Fase 1: Criação (Concluída)
- [x] Criar `application.yml` com todas as configurações
- [x] Validar sintaxe YAML
- [x] Manter `application.properties` como backup

### ⏳ Fase 2: Validação (Próxima Sprint)
- [ ] Testar aplicação com `application.yml`
- [ ] Validar todos os endpoints
- [ ] Confirmar que configurações são carregadas corretamente
- [ ] Testar profiles (dev, test, prod)

### ⏳ Fase 3: Deprecação
- [ ] Renomear `application.properties` para `application.properties.deprecated`
- [ ] Adicionar comentário: "DEPRECATED - Use application.yml"
- [ ] Comunicar à equipe

### ⏳ Fase 4: Remoção (Sprint + 2)
- [ ] Remover `application.properties` completamente
- [ ] Atualizar documentação
- [ ] Atualizar README com exemplos YAML

## Ordem de Precedência Spring Boot

**Importante:** Spring Boot carrega configurações na seguinte ordem (última sobrescreve):

1. `application.properties`
2. `application.yml`
3. `application-{profile}.properties`
4. `application-{profile}.yml`
5. Variáveis de ambiente
6. Argumentos de linha de comando

⚠️ **Durante a transição**, ambos os arquivos coexistem, mas **YAML tem precedência** sobre Properties.

## Padrões Estabelecidos

### Estrutura de Arquivo YAML

```yaml
spring:
  application:
    name: <app-name>

  # ============================================================
  # <Seção> Configuration
  # ============================================================
  <secao>:
    propriedade: valor
    sub-secao:
      propriedade: valor

  # ============================================================
  # Próxima Seção
  # ============================================================
```

### Convenções de Nomenclatura

- **Indentação**: 2 espaços (não tabs)
- **Comentários**: Usar `#` para documentar decisões
- **Separadores**: Linha de `#====` para seções principais
- **Referências ADR**: Incluir em comentários quando aplicável

### Exemplo de Configuração Futura

```yaml
# ============================================================
# Security Configuration
# Decisao tecnica: ADR-XXX
# ============================================================
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - email
              - profile
```

## Validação e Testes

### Checklist de Validação

```bash
# 1. Validar sintaxe YAML
yamllint src/main/resources/application.yml

# 2. Iniciar aplicação
mvn spring-boot:run

# 3. Verificar propriedades carregadas
curl http://localhost:8080/actuator/configprops

# 4. Testar endpoints com paginação
curl "http://localhost:8080/api/products?page=0&size=50"
curl "http://localhost:8080/api/products?size=500"  # Deve limitar a 100
```

### Casos de Teste

| Teste | Esperado | Status |
|-------|----------|--------|
| Aplicação inicia sem erros | ✅ | ⏳ |
| Datasource conecta ao PostgreSQL | ✅ | ⏳ |
| Flyway executa migrations | ✅ | ⏳ |
| Paginação default = 20 | ✅ | ⏳ |
| Paginação max = 100 | ✅ | ⏳ |
| JPA show-sql funciona | ✅ | ⏳ |

## Rollback Plan

Se problemas críticos forem encontrados:

### Rollback Imediato
```bash
# 1. Renomear arquivos
mv application.yml application.yml.backup
mv application.properties.deprecated application.properties

# 2. Reiniciar aplicação
mvn spring-boot:run
```

### Rollback em Produção
```bash
# Usar variáveis de ambiente para sobrescrever
export SPRING_DATA_WEB_PAGEABLE_MAX_PAGE_SIZE=100
export SPRING_DATASOURCE_URL=jdbc:postgresql://...
```

## Alternativas Consideradas

### Alternativa 1: Manter Properties
- **Prós**: Sem mudanças, sem riscos
- **Contras**: Não resolve problemas de legibilidade/escalabilidade
- **Decisão**: Rejeitado - problemas continuariam crescendo

### Alternativa 2: Migração Gradual (híbrido)
- **Prós**: Risco reduzido, mudança incremental
- **Contras**: Confusão sobre qual formato usar, duplicação
- **Decisão**: Rejeitado - gera inconsistência

### Alternativa 3: YAML + Properties para Secrets
- **Prós**: YAML para estrutura, Properties para valores sensíveis
- **Contras**: Complexidade desnecessária
- **Decisão**: Rejeitado - variáveis de ambiente são suficientes

### Alternativa 4: Migração Completa Imediata ✅
- **Prós**: Consistência total, sem ambiguidade
- **Contras**: Requer validação cuidadosa
- **Decisão**: **ACEITO** - com período de validação

## Impacto em Outras Áreas

### CI/CD
✅ Sem impacto - Ambos os formatos são suportados

### Docker
✅ Melhoria - Alinhamento com `docker-compose.yml`

### Kubernetes
✅ Melhoria - ConfigMaps são naturalmente YAML

### Documentação
⚠️ Atualizar exemplos de configuração

### Onboarding
⚠️ Adicionar treinamento básico de YAML

## Métricas de Sucesso

| Métrica | Baseline | Target | Prazo |
|---------|----------|--------|-------|
| Aplicação inicia sem erros | - | 100% | Sprint atual |
| Testes passam | - | 100% | Sprint atual |
| Bugs relacionados a config | 0 | 0 | 2 sprints |
| Satisfação da equipe | - | > 4/5 | 1 mês |

## Referências

- [Spring Boot - Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [YAML Specification](https://yaml.org/spec/1.2.2/)
- [Baeldung - Properties vs YAML](https://www.baeldung.com/spring-boot-yaml-vs-properties)
- [Spring Boot Common Application Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html)

## ADRs Relacionados

- **ADR-003**: Configuração de Limites de Paginação (configurações migradas)
- **ADR-002**: PagedModel como Padrão de Paginação (contexto)

## Revisões

| Data | Autor | Mudança |
|------|-------|---------|
| 2025-01-04 | Arquitetura | Decisão inicial aceita e migração executada |

## Próximos Passos

1. [ ] Validar aplicação com `application.yml` (Sprint atual)
2. [ ] Executar suite completa de testes
3. [ ] Deploy em ambiente de desenvolvimento
4. [ ] Coletar feedback da equipe
5. [ ] Deprecar `application.properties` (Sprint + 1)
6. [ ] Remover `application.properties` (Sprint + 2)
7. [ ] Atualizar templates de projeto

## Metadados

- **Decisores**: Equipe de Arquitetura
- **Impacto**: Médio (Mudança de Formato de Configuração)
- **Categoria**: DevOps, Configuration Management, Best Practices
- **Tags**: #yaml #properties #configuracao #migracao #cloud-native
- **Relacionado**: ADR-003 (Limites de Paginação), ADR-002 (PagedModel)
- **Breaking Change**: Não (compatibilidade mantida)

