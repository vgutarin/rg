# Research

## Permissions model idea

### Global and local permissions
-  Global: 
  - enabled
  - disabled
  - group (may be not even required - any user can manage groups?)
    - :manage
    - or  :add, :edit...
- local
  - precise rules applied inside groups
    - like user:C|R|U|D, event:C|R|U|D ...
    - customization for any resource inside groups

NOTE:
  - groups are always belong to some user
  - user also may have some resources in personal space.
  - may be we would like to add some 'public' space later and any resource may be published to this space


Each resource may have optional permission object. Similar to

```java

record Permissions (
        Set<String> anyone, // Permissions for all users and applications (users including anonymous)
        Set<String> registered, // Permissions for all registered users and applications (no anonymous),
        Map<UniqueId, Set<String>> applicationPermissions, // Permissions for particular application (application may show announces issued by another app)    
         /*??? or*/ Map<UniqueId, Set<String>> groupPermissions, // Permissions for particular application (application may show announces issued by another app)    
        Map<UniqueId, Set<String>> applicationUserPermissions, // Permissions for particular application users (??? may be excessive)   
        Map<UniqueId, Set<String>> userPermissions // Permissions for particular users    
){
    
}

record PermissionsNode (
    UniqueId inheritFromResource, // nullable
    Permission delete,
    Permissions add
) { }
```

### Decision to make

 - What is default behavior when no permissions are given? (Options: only author have access?)

### DB
  
- `rg_permission` table. Keeps normalized json {UniqueId, hashOfJson, normalized JSON} 
  - use hash to find JSON to avoid duplicates (i think we will have a lot of just inheritance)
  - can be immutable
- `rg_resource_permission_node` table {resourceUniqueId PK, inheritFromResource, permissionAddUniqueID, permissionDeleteUniqueID}

```SQL
  SELECT 
      `r`.*,
      true AS `hasPermissionsNode` -- if permissions are specified false if is not
  FROM `resource` AS `r`
  LEFT JOIN `rg_resource_permission` AS `rp` ON `rp`.`resourceUniqueId` = `r`.`uniqueId`
```

result informs shall we use default or do actual calculations

### Service

by given `resourceUniqueId` builds final `Permissions` object traversing by all tree. Uses cache for result. Same cache must be used during getting permissions for inheritFromResource

## TODO

- remove(cleanup) dialogs for timepickers
- consider to create component to show `Label: (Sat) [date] [time]` as 1 line
- make sure timezone is applied correctly
- upgrade to java 25
- analyze tests to possible refactor. I.e. - authentication helpers can be centralized etc
- make sure EnableMethodSecurity is enabled on logic model level
- add user contacts - Some structure related to user. {uniqueId, holderUniqueId, contactUniqueId nullable, ... } (unique(holderUniqueId, contactUniqueId))
  - (use uniqueId for all further refs)
- add user groups - group is subset of contacts 
- Go over paginable methods (list locations, list workspaces, filters) and make sure we  have ORDER BY
- UI
  - when Add location tab is opened - activate Map selector. Do not show tab if no permission to add
  - when permission is unknow - UI is blocked with "Obsolete APP" message. Need solution
  - Some errors happed on saving location to db level - UI became frozen.
    - we need some generic for all app approach to show/handle errors on frontend
    - any action on connection losted - should clearly say so
    - any error should be explicitly informed (no unresponsive behavior)
- consider to do not recognize permissions every time. Do it one time on principal creation (TODO)
- remove "logout" button for telegram mini app
- consider to recover integration-tests? or configure func tests to use mysql
- Clean up "\* Template \*"
- add and use bom project with
  - versions of apis/implementations
  - test util version
- add ACL
- add Audit
- add common errors (like, "Version conflict", "Access denied", "Validation error" etc)
