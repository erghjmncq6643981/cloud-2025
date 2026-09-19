package db

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"time"

	_ "github.com/lib/pq"
)

// DBClient 数据库客户端单例封装
type DBClient struct {
	db *sql.DB
}

var globalDB *DBClient

// InitDB 初始化 PostgreSQL 数据库连接池
func InitDB(dsn string) (*DBClient, error) {
	if dsn == "" {
		return nil, fmt.Errorf("PG_DSN 未配置")
	}

	db, err := sql.Open("postgres", dsn)
	if err != nil {
		return nil, fmt.Errorf("打开 PostgreSQL 连接失败: %w", err)
	}

	// 生产级连接池调优
	db.SetMaxOpenConns(25)
	db.SetMaxIdleConns(10)
	db.SetConnMaxLifetime(5 * time.Minute)
	db.SetConnMaxIdleTime(1 * time.Minute)

	// 探活
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	if err := db.PingContext(ctx); err != nil {
		return nil, fmt.Errorf("PostgreSQL 探活 Ping 失败: %w", err)
	}

	client := &DBClient{db: db}
	globalDB = client
	log.Printf("✅ [PostgreSQL] 成功连接至 FreeSWITCH 核心数据库")
	return client, nil
}

// GetDB 获取底层 sql.DB
func (c *DBClient) GetDB() *sql.DB {
	return c.db
}

// Ping 检测连接状态
func (c *DBClient) Ping() bool {
	if c == nil || c.db == nil {
		return false
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	return c.db.PingContext(ctx) == nil
}

// Close 关闭连接
func (c *DBClient) Close() error {
	if c != nil && c.db != nil {
		return c.db.Close()
	}
	return nil
}
