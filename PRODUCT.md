# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin with Android Views/XML.

## Users

Primary user is a viewer on a sofa using a physical Android TV remote after television startup. The user wants to open a favorite application quickly, browse installed applications, switch television inputs, or change launcher settings.

## Product Purpose

QuasarTV is a premium, ultra-lightweight native Android TV launcher. It provides fast access to favorite applications, installed applications, device-reported inputs, and launcher settings without advertisements, recommendations, promotional content, or unnecessary complexity.

Success means a viewer can open a favorite application with one OK press and can understand focus, availability, and return behavior from the sofa.

## Positioning

QuasarTV is a focused launcher for fast application access rather than a content feed. Its distinct mechanism is a deliberately quiet home screen that makes the focused application the primary launch action.

## Operating Context

The target device is a Xiaomi Mi TV 4A running Android 9 with 1 GB RAM and a low-end GPU. The launcher must remain readable at 720p and 1080p and usable exclusively through a physical remote.

## Capabilities and Constraints

- Discover and launch installed Android TV applications.
- Browse all launchable installed applications clearly.
- Manage, add, remove, and reorder favorite applications.
- Discover actual HDMI, AV, Live TV, and other Android TV inputs where supported.
- Use Android's system input picker when direct switching is restricted or unavailable.
- Provide launcher appearance, background, motion, density, favorite-management, system-settings, and About controls.
- Use native Kotlin and Android Views/XML.
- Avoid ads, recommendations, promotional feeds, onboarding delay, touch dependency, unsupported hardware claims, continuous backgrounds, blur, particles, and expensive GPU effects.
- Keep idle launcher PSS within 15–25 MiB where practical.

## Brand Commitments

The product name is QuasarTV. The interface must feel cinematic, elegant, modern, minimal, and responsive while preserving clear native Android TV affordances.

## Evidence on Hand

The repository contains the Android launcher implementation, approved surface brief, platform discovery code, and vector launcher identity assets. No external brand imagery, customer evidence, testimonials, or commercial claims are available.

## Product Principles

- Prioritize one-press access to favorite apps.
- Keep the launcher quiet: no feed, ads, recommendations, or promotional detours.
- Show only real device capabilities and explain unavailable states.
- Make every remote interaction predictable, visible, and reversible.
- Prefer static composition and bounded motion over expensive effects.

## Accessibility & Inclusion

The launcher must work without touch, expose meaningful spoken labels, maintain clear D-pad focus, support reduced or disabled motion, preserve readability from sofa distance, and keep localization possible through Android resources.
