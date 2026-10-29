package com.ournight.api_server.domain.store.model

import com.ournight.api_server.domain.store.domain.Store
import com.ournight.api_server.domain.store.domain.StoreMenu
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "store_menu_category")
@EntityListeners(AuditingEntityListener::class)
class StoreMenuCategory(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var sequence: Int = 0, // 카테고리 노출 순서

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    val store: Store,

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    val menus: MutableList<StoreMenu> = mutableListOf(),

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
)