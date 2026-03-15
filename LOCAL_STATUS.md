# Local development status (2026-03-12)

## Situation
GitHub authentication problems prevented pushing the latest commits to origin/master.

Work continues locally. The repository state is safe.

## Local commits not yet pushed

4a3abce  
fix(validation): remove committed merge conflict markers in A2OutOfRangePositionTest

3534cd9  
fix(tests): align RunLayoutFactoryTest with dc/{exports,results} layout

## Backup artifacts

The work has been secured with:

- `master-fixes.bundle`
- Full disk copy of the repository created on 2026-03-12

## Recovery options

When GitHub access is restored, push normally:


git push origin master


Alternative: apply commits from bundle


git fetch master-fixes.bundle master
git cherry-pick 4a3abce 3534cd9
git push origin master


## Notes

Development continues locally until GitHub authentication is resolved.