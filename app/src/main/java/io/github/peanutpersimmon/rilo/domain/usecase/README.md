# Use cases / domain services

Feature-facing use cases live here. Session overlap enforcement, active-session handling, Category classification, and statistics each have one domain implementation. Pure shared policies live under `domain/service`; Room transactions are coordinated by `RoomRidoRepository`. UI and Composables must not reproduce these decisions.
