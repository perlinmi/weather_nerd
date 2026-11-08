# This project was created for testing spec driven development approach

## 1 day of coding 08.Nov 2026

### Achievments
- implemented 5 features
- implemented 2 refactorings
- migration from Spring Boot 3 to Spring Boot 4

### Completely ignored
- usage of cloud services
- usage of storages
- only one external data provider
- code delivery pipeline after build step
- deployment (runs as docker compose locally) 

### Development approach
- Spec Driven Development with GitHub Speckit by the (book)[https://speckit.org]
    * `/speckit.specify`
    * `/speckit.clarify`
    * `/speckit.plan`
    * `/speckit.tasks`
    * `/speckit.analyze`
    * `/speckit.implement`
- Greenfield!
- Monorepo
- Tools: VSCode, GitHub Copilot, Docker
- ignoring how many tokens used, just ute model proposed by copilot (fusion mode)

### What is good
- very intuitive, lerning curve very flat, initial setup and writing guidelines took 1h
- no sigle line of code writen
- interview very detailed, generating good proposals, asking for edge cases
- when implementing feature 005 also taking into account what was impplemented in features 001-004, adapting specs and plans, also adapting general guidelines
- AI manages implementation step completely, getting generated code really running: discovering test failure it analyzes the reason, takes actions and reruns again until everything succeeds

### What is less good
- very shallow software design. AI follows only general Java/Spring practices by structuring code into packages/classes and generating tests, which produces harly maintainable code if not superwised by engineer. You need to define design principles in AGENTS.md and docs/testing.md

### Bottom line
For greenfield project of low complexity/size it would be feasible to develop with one product owner and 1/2 midlevel engineers and 0.5 QA engineers, following this approach to generate working and maintainable code very effective.

For project of middle complexity this approach also looks feasible, but this needs proper validation

### Next steps
Implement harder challenges to get this project to at least middle complexity, so that AI faces
- integration of cloud/infrastructure services
- non-functional requirement for scalability, reliability, etc.