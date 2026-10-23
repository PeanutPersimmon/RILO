# Repository contracts

Repository contracts owned by the domain layer belong here. They expose domain models, observable reads and use-case-oriented suspend commands without leaking Room DAO, Entity or cursor types. The current Room transaction implementation is `data/repository/RoomRidoRepository`; narrower feature contracts are added with the use cases that consume them.
