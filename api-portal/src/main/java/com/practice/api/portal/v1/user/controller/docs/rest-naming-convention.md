# REST API Naming Convention — Plural vs Singular

## Tại sao dùng `/users` thay vì `/user`?

`/users` đại diện cho **collection** (tập hợp), không phải một object cụ thể.

Giống như file system:

```
/documents/          ← thư mục (collection)
/documents/file.txt  ← một file cụ thể trong thư mục
```

REST map vào đó:

```
POST   /users        → thêm 1 user VÀO collection
GET    /users        → lấy toàn bộ collection
GET    /users/{id}   → lấy 1 user CỤ THỂ trong collection
PUT    /users/{id}   → sửa 1 user CỤ THỂ
DELETE /users/{id}   → xóa 1 user CỤ THỂ
```

`POST /users` nghĩa là **"tạo một phần tử mới trong collection users"** — nên dùng số nhiều là đúng, dù chỉ tạo 1 user.

## Quy tắc chung

- **Noun trong URL, verb trong HTTP method**
- **Số nhiều** cho collection endpoint
- **Số nhiều + `/{id}`** cho single resource endpoint

## Ngoại lệ — sub-path cho action không thuộc CRUD

```
POST /api/v1/users/{id}/activate        ← activate account
POST /api/v1/users/{id}/reset-password  ← reset password
POST /api/v1/users/{id}/roles           ← assign role
```
