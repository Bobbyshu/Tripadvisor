# TripAdvisor Backend App

This project is a backend application mimicking some functionalities of **TripAdvisor**. It is developed as part of my learning journey to better understand and implement **Redis** in a real-world application. The app handles key business logic and provides API endpoints for a variety of actions related to users, shops, vouchers, and more.

## Purpose

The goal of this project is to:
- Understand and implement Redis for caching and database optimization.
- Simulate a backend system similar to **TripAdvisor**.
- Learn how to design a backend system with real-world data models.

## Features

The backend app includes several advanced Redis-based features such as:

- **SMS Login**: Redis shared session management.
- **Merchant Query Cache**: Caching merchant data.
- **Enterprise Cache Techniques**: Solutions for cache avalanche and penetration.
- **Coupon Seckill**: High-concurrency coupon system.
- **Redis Counters & Lua Scripts**: Atomic counters and Lua scripting.
- **Distributed Locks**: Redis-based distributed locking.
- **Redis Message Queues**: Three types of Redis message queues.
- **Nearby Merchants**: Redis GeoHash for location-based queries.
- **Black Horse Review**: Review system with Redis.
- **Influencer Shop Reviews**: User blogs for merchant reviews.
- **Like Lists**: Redis List-based like tracking.
- **Leaderboard**: Redis SortedSet for ranking.
- **Friend Follow System**: Redis Set for follow/unfollow and notifications.
- **User Check-ins**: Redis Bitmap for check-in tracking.
- **UV Statistics**: Redis HyperLogLog for unique visitor counting.

## Database Tables

The project uses the following tables:

- **tb_user**: User table for storing basic user information.
- **tb_user_info**: Stores additional user details, such as profile information.
- **tb_shop**: Stores shop information, including details about different merchants.
- **tb_shop_type**: Categorizes different types of shops.
- **tb_blog**: Users can post blogs or reviews, with this table storing blog entries.
- **tb_follow**: Tracks user follow relationships, allowing users to follow other users or shops.
- **tb_voucher**: Contains information about available discount coupons.
- **tb_voucher_order**: Stores orders related to vouchers, tracking user redemptions.

## Technologies Used

- **Java**: Programming language used to build the application.
- **Spring Boot**: Framework for creating the backend application.
- **Redis**: Used for caching and optimizing performance.
- **MySQL**: Relational database to store persistent data.
- **JPA**: Java Persistence API for database interactions.
