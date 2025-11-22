# SWE242P

## Run

### Assignment 2

```bash
cd ./out/production/SWE242P
```

```bash
java assignment2.LineCount <file1> <file2> ...
```

### Assignment 3

```bash
cd ./out/production/SWE242P
```

```bash
java assignment3.FileServer <directory_path>
```

### Assignment 4

```bash
cd ./out/production/SWE242P
```

```bash
java assignment4.UDPServer <directory_path>
```

## Test

### Assignment 4

#### index

```bash
index
```

#### get

```bash
get index.html
```

#### unexpected command

```bash
set
```

#### file not found

```bash
get xxx.html
```

#### get multi chunks file

```bash
get multi_chunks.html
```

#### charset test, get Chinese multi chunks file

```bash
get chinese_multi_chunks.html
```

#### packet loss or timeout

```markdown
uncomment debug section in UDPServer.java
```