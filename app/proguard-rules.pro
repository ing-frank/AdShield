# Reglas específicas de AD Shield.
# Room, WorkManager, DataStore y Compose incluyen sus propias reglas de consumidor.

# Los enums se guardan por nombre (Room y DataStore): no deben renombrarse.
-keepclassmembers enum com.adshield.domain.model.** { *; }
