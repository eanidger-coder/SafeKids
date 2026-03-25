# SafeKids Proguard Rules

# Keep Room entities
-keep class com.safekids.data.entities.** { *; }

# Keep Room DAOs
-keep class com.safekids.data.dao.** { *; }

# Keep ContentClassifier for tests
-keep class com.safekids.core.** { *; }
